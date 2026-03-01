//
//  AppState.swift
//  EdgeClaw
//
//  Global observable app state wrapping the Rust EdgeClawEngine via UniFFI.
//

import Foundation
import Combine

// MARK: - Mirror types (match UniFFI-generated structs)

/// Device identity — mirrors Rust DeviceIdentity
struct ECDeviceIdentity: Identifiable, Codable {
    var id: String { deviceId }
    let deviceId: String
    let publicKeyHex: String
    let fingerprint: String
    let createdAt: String
}

/// Peer information — mirrors Rust PeerInfo
struct ECPeerInfo: Identifiable, Codable {
    var id: String { peerId }
    let peerId: String
    let deviceName: String
    let deviceType: String
    let address: String
    let capabilities: [String]
    let lastSeen: String
    var isConnected: Bool
}

/// Session information — mirrors Rust SessionInfo
struct ECSessionInfo: Identifiable, Codable {
    var id: String { sessionId }
    let sessionId: String
    let peerId: String
    let state: String
    let createdAt: String
    let expiresAt: String
    let messagesSent: UInt64
    let messagesReceived: UInt64
}

/// Policy decision — mirrors Rust PolicyDecision
struct ECPolicyDecision: Codable {
    let allowed: Bool
    let reason: String
    let riskLevel: UInt8
}

// MARK: - App State

/// Central application state — publishes changes to all SwiftUI views.
///
/// When the real UniFFI bindings are generated, replace the stub calls
/// with the generated `EdgeClawEngine` interface.
@MainActor
class AppState: ObservableObject {
    // MARK: Published state
    @Published var identity: ECDeviceIdentity?
    @Published var peers: [ECPeerInfo] = []
    @Published var sessions: [ECSessionInfo] = []
    @Published var isEngineReady = false
    @Published var isSyncConnected = false
    @Published var statusMessage = "Initializing…"
    @Published var errorMessage: String?

    // Engine uptime
    @Published var uptimeSeconds: UInt64 = 0

    // MARK: Engine reference (UniFFI)
    // Replace `Any?` with the actual UniFFI-generated type once available:
    //   private var engine: EdgeClawEngine?
    private var engine: Any?
    private var uptimeTimer: Timer?

    // MARK: - Lifecycle

    func initializeEngine() {
        do {
            // TODO: Replace with real UniFFI call:
            // engine = try createEngine(config: EngineConfig(…))
            isEngineReady = true
            statusMessage = "Engine ready"

            startUptimeTimer()
        } catch {
            errorMessage = "Engine init failed: \(error.localizedDescription)"
            statusMessage = "Error"
        }
    }

    // MARK: - Identity

    func generateIdentity() {
        guard isEngineReady else { return }
        do {
            // TODO: Replace with: let id = try engine!.generateIdentity()
            let id = ECDeviceIdentity(
                deviceId: UUID().uuidString,
                publicKeyHex: String(repeating: "0", count: 64),
                fingerprint: String(repeating: "a", count: 16),
                createdAt: ISO8601DateFormatter().string(from: Date())
            )
            identity = id
            statusMessage = "Identity generated"
        } catch {
            errorMessage = "Identity generation failed: \(error.localizedDescription)"
        }
    }

    // MARK: - Peers

    func addPeer(peerId: String, name: String, type: String,
                 address: String, capabilities: [String]) {
        guard isEngineReady else { return }
        do {
            // TODO: Replace with engine call
            let peer = ECPeerInfo(
                peerId: peerId,
                deviceName: name,
                deviceType: type,
                address: address,
                capabilities: capabilities,
                lastSeen: ISO8601DateFormatter().string(from: Date()),
                isConnected: false
            )
            peers.append(peer)
            statusMessage = "Peer added: \(name)"
        } catch {
            errorMessage = "Add peer failed: \(error.localizedDescription)"
        }
    }

    func removePeer(peerId: String) {
        peers.removeAll { $0.peerId == peerId }
    }

    func refreshPeers() {
        // TODO: peers = engine?.getPeers() ?? []
    }

    // MARK: - Sessions

    func createSession(peerId: String, peerPublicKey: [UInt8]) {
        guard isEngineReady else { return }
        // TODO: let session = try engine!.createSession(peerId, peerPublicKey)
        let session = ECSessionInfo(
            sessionId: UUID().uuidString,
            peerId: peerId,
            state: "established",
            createdAt: ISO8601DateFormatter().string(from: Date()),
            expiresAt: ISO8601DateFormatter().string(
                from: Date().addingTimeInterval(3600)),
            messagesSent: 0,
            messagesReceived: 0
        )
        sessions.append(session)
    }

    // MARK: - Policy

    func evaluateCapability(name: String, role: String) -> ECPolicyDecision? {
        // TODO: return try engine!.evaluateCapability(name, role)
        return ECPolicyDecision(allowed: true, reason: "stub", riskLevel: 0)
    }

    // MARK: - Sync

    func initSync(desktopAddress: String, port: UInt16 = 8443) {
        // TODO: engine!.initSync(SyncClientConfig(…))
        statusMessage = "Sync initialized → \(desktopAddress):\(port)"
    }

    func checkSyncConnection() {
        // TODO: isSyncConnected = engine!.syncIsConnected()
    }

    func syncShutdown() {
        // TODO: engine!.syncShutdown()
        isSyncConnected = false
    }

    // MARK: - Timer

    private func startUptimeTimer() {
        uptimeTimer = Timer.scheduledTimer(withTimeInterval: 1.0,
                                           repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.uptimeSeconds += 1
            }
        }
    }

    deinit {
        uptimeTimer?.invalidate()
    }
}
