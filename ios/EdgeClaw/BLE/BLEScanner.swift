//
//  BLEScanner.swift
//  EdgeClaw
//
//  CoreBluetooth-based BLE scanner for discovering EdgeClaw peers.
//  Scans for devices advertising the EdgeClaw BLE service UUID.
//

import Foundation
import CoreBluetooth
import Combine

/// EdgeClaw BLE Service UUID (custom 128-bit UUID)
let kEdgeClawServiceUUID = CBUUID(string: "EC1A0001-ED6E-4C4C-8A1B-000000000001")

/// Characteristic UUID for device identity advertisement
let kIdentityCharUUID = CBUUID(string: "EC1A0002-ED6E-4C4C-8A1B-000000000002")

/// Discovered BLE peer
struct BLEPeer: Identifiable {
    let id: UUID
    let name: String
    let rssi: Int
    let deviceId: String?
    let discoveredAt: Date
}

/// BLE scanner for EdgeClaw peer discovery.
///
/// Usage:
/// ```swift
/// let scanner = BLEScanner()
/// scanner.startScanning()
/// // observe scanner.discoveredPeers
/// scanner.stopScanning()
/// ```
class BLEScanner: NSObject, ObservableObject {
    @Published var discoveredPeers: [BLEPeer] = []
    @Published var isScanning = false
    @Published var bluetoothState: CBManagerState = .unknown

    private var centralManager: CBCentralManager!
    private var seenPeripherals: [UUID: CBPeripheral] = [:]

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: .main)
    }

    /// Start scanning for EdgeClaw BLE devices
    func startScanning() {
        guard centralManager.state == .poweredOn else {
            print("[BLE] Bluetooth not ready: \(centralManager.state.rawValue)")
            return
        }

        discoveredPeers.removeAll()
        seenPeripherals.removeAll()
        isScanning = true

        centralManager.scanForPeripherals(
            withServices: [kEdgeClawServiceUUID],
            options: [
                CBCentralManagerScanOptionAllowDuplicatesKey: false
            ]
        )

        print("[BLE] Scanning started for EdgeClaw service")
    }

    /// Stop scanning
    func stopScanning() {
        centralManager.stopScan()
        isScanning = false
        print("[BLE] Scanning stopped")
    }

    /// Connect to a discovered peer to read its identity
    func connect(to peer: BLEPeer) {
        guard let peripheral = seenPeripherals[peer.id] else { return }
        centralManager.connect(peripheral, options: nil)
    }

    /// Disconnect from a peer
    func disconnect(from peer: BLEPeer) {
        guard let peripheral = seenPeripherals[peer.id] else { return }
        centralManager.cancelPeripheralConnection(peripheral)
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEScanner: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        bluetoothState = central.state
        print("[BLE] State: \(central.state.rawValue)")

        if central.state == .poweredOn && isScanning {
            startScanning()
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        let name = peripheral.name
            ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String
            ?? "Unknown"

        // Extract device ID from manufacturer data if present
        var deviceId: String?
        if let mfgData = advertisementData[CBAdvertisementDataManufacturerSpecificDataKey] as? Data,
           mfgData.count >= 16 {
            deviceId = mfgData.prefix(16).map { String(format: "%02x", $0) }.joined()
        }

        let peer = BLEPeer(
            id: peripheral.identifier,
            name: name,
            rssi: RSSI.intValue,
            deviceId: deviceId,
            discoveredAt: Date()
        )

        seenPeripherals[peripheral.identifier] = peripheral

        // Update or append
        if let idx = discoveredPeers.firstIndex(where: { $0.id == peer.id }) {
            discoveredPeers[idx] = peer
        } else {
            discoveredPeers.append(peer)
        }

        print("[BLE] Discovered: \(name) RSSI=\(RSSI) id=\(deviceId ?? "?")")
    }

    func centralManager(_ central: CBCentralManager,
                        didConnect peripheral: CBPeripheral) {
        peripheral.delegate = self
        peripheral.discoverServices([kEdgeClawServiceUUID])
        print("[BLE] Connected to \(peripheral.name ?? "?")")
    }

    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        print("[BLE] Disconnected from \(peripheral.name ?? "?")")
    }
}

// MARK: - CBPeripheralDelegate

extension BLEScanner: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == kEdgeClawServiceUUID {
            peripheral.discoverCharacteristics(
                [kIdentityCharUUID],
                for: service
            )
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        guard let chars = service.characteristics else { return }
        for char in chars where char.uuid == kIdentityCharUUID {
            peripheral.readValue(for: char)
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard let data = characteristic.value else { return }
        if let identity = String(data: data, encoding: .utf8) {
            print("[BLE] Identity characteristic: \(identity)")
        }
    }
}
