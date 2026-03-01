//
//  TCPClient.swift
//  EdgeClaw
//
//  NWConnection-based TCP client for connecting to EdgeClaw Desktop agent.
//  Sends/receives ECNP v1.1 binary frames over TCP.
//

import Foundation
import Network
import Combine

/// TCP connection state
enum TCPConnectionState: String {
    case disconnected
    case connecting
    case connected
    case failed
}

/// TCP client for EdgeClaw Desktop agent communication.
///
/// Uses Apple's Network framework (NWConnection) for modern TCP/TLS.
/// Supports ECNP v1.1 binary framing natively.
class TCPClient: ObservableObject {
    @Published var state: TCPConnectionState = .disconnected
    @Published var lastError: String?

    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "com.edgeclaw.tcp", qos: .userInitiated)

    /// Frame callback — called when a complete ECNP frame is received
    var onFrameReceived: ((Data) -> Void)?

    /// Statechange callback
    var onStateChanged: ((TCPConnectionState) -> Void)?

    // ECNP constants
    private let ecnpHeaderSize = 6 // version(1) + type(1) + length(4)

    // MARK: - Connect

    /// Connect to an EdgeClaw Desktop agent via TCP
    func connect(host: String, port: UInt16, useTLS: Bool = false) {
        disconnect()

        let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port)!
        )

        let params: NWParameters
        if useTLS {
            params = .tls
        } else {
            params = .tcp
        }

        // TCP keepalive
        params.requiredInterfaceType = .wifi

        connection = NWConnection(to: endpoint, using: params)
        connection?.stateUpdateHandler = { [weak self] newState in
            DispatchQueue.main.async {
                self?.handleStateChange(newState)
            }
        }

        updateState(.connecting)
        connection?.start(queue: queue)
    }

    /// Disconnect from the desktop agent
    func disconnect() {
        connection?.cancel()
        connection = nil
        updateState(.disconnected)
    }

    // MARK: - Send

    /// Send raw data over the TCP connection
    func send(data: Data, completion: ((Error?) -> Void)? = nil) {
        guard state == .connected, let conn = connection else {
            completion?(NSError(domain: "EdgeClaw", code: -1,
                                userInfo: [NSLocalizedDescriptionKey: "Not connected"]))
            return
        }

        conn.send(content: data, completion: .contentProcessed { error in
            if let error = error {
                print("[TCP] Send error: \(error)")
            }
            completion?(error)
        })
    }

    /// Send an ECNP-framed message
    func sendECNP(msgType: UInt8, payload: Data, completion: ((Error?) -> Void)? = nil) {
        var frame = Data(capacity: ecnpHeaderSize + payload.count)
        frame.append(0x01)  // ECNP version 1
        frame.append(msgType)

        // Length as big-endian u32
        var length = UInt32(payload.count).bigEndian
        frame.append(Data(bytes: &length, count: 4))
        frame.append(payload)

        send(data: frame, completion: completion)
    }

    // MARK: - Receive

    /// Start the receive loop — reads ECNP frames continuously
    private func startReceiveLoop() {
        receiveNextFrame()
    }

    private func receiveNextFrame() {
        guard let conn = connection else { return }

        // First, read the 6-byte header
        conn.receive(minimumIncompleteLength: ecnpHeaderSize,
                     maximumLength: ecnpHeaderSize) { [weak self] data, _, _, error in
            guard let self = self else { return }

            if let error = error {
                print("[TCP] Receive header error: \(error)")
                return
            }

            guard let headerData = data, headerData.count == self.ecnpHeaderSize else {
                print("[TCP] Incomplete header")
                return
            }

            // Parse header
            let version = headerData[0]
            let msgType = headerData[1]
            let payloadLen = UInt32(headerData[2]) << 24
                | UInt32(headerData[3]) << 16
                | UInt32(headerData[4]) << 8
                | UInt32(headerData[5])

            guard version == 0x01, payloadLen <= 1_048_576 else {
                print("[TCP] Invalid ECNP header: v=\(version) len=\(payloadLen)")
                return
            }

            if payloadLen == 0 {
                // No payload — deliver header-only frame
                DispatchQueue.main.async {
                    self.onFrameReceived?(headerData)
                }
                self.receiveNextFrame()
                return
            }

            // Read payload
            conn.receive(minimumIncompleteLength: Int(payloadLen),
                         maximumLength: Int(payloadLen)) { [weak self] payloadData, _, _, error in
                guard let self = self else { return }

                if let error = error {
                    print("[TCP] Receive payload error: \(error)")
                    return
                }

                guard let payload = payloadData else { return }

                // Assemble full frame
                var frame = headerData
                frame.append(payload)

                DispatchQueue.main.async {
                    self.onFrameReceived?(frame)
                }

                // Continue reading
                self.receiveNextFrame()
            }
        }
    }

    // MARK: - State

    private func handleStateChange(_ nwState: NWConnection.State) {
        switch nwState {
        case .ready:
            updateState(.connected)
            startReceiveLoop()
        case .failed(let error):
            lastError = error.localizedDescription
            updateState(.failed)
        case .cancelled:
            updateState(.disconnected)
        case .preparing, .setup:
            updateState(.connecting)
        case .waiting(let error):
            lastError = error.localizedDescription
            updateState(.connecting)
        @unknown default:
            break
        }
    }

    private func updateState(_ newState: TCPConnectionState) {
        state = newState
        onStateChanged?(newState)
    }
}
