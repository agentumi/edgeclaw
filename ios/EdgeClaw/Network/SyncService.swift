//
//  SyncService.swift
//  EdgeClaw
//
//  Background synchronization service for syncing with EdgeClaw Desktop agent.
//  Uses BGAppRefreshTask (iOS 13+) for periodic background sync.
//

import Foundation
import BackgroundTasks
import Combine
import Network

/// Background sync task identifier
let kSyncTaskIdentifier = "com.edgeclaw.mobile.sync"
let kSyncRefreshIdentifier = "com.edgeclaw.mobile.sync.refresh"

/// Sync direction
enum SyncDirection: String, Codable {
    case push    // Mobile → Desktop
    case pull    // Desktop → Mobile
    case bidir   // Bidirectional
}

/// Sync status
enum SyncStatus: String, Codable {
    case idle
    case syncing
    case completed
    case failed
    case scheduled
}

/// Sync item — a single piece of data to synchronize
struct SyncItem: Identifiable, Codable {
    let id: String
    let type: SyncItemType
    let data: Data
    let timestamp: Date
    let direction: SyncDirection
    var synced: Bool
}

/// Types of data that can be synced
enum SyncItemType: String, Codable {
    case config        // Configuration settings
    case identity      // Device identity
    case peerList      // Known peers
    case sessionState  // Active session states
    case policyUpdate  // Policy/RBAC updates
    case activityLog   // Activity log entries
    case statusPush    // Status push from desktop
}

/// Sync result for a single sync operation
struct SyncResult: Codable {
    let itemsSent: Int
    let itemsReceived: Int
    let errors: [String]
    let duration: TimeInterval
    let timestamp: Date
}

/// Background sync service for EdgeClaw Desktop communication.
///
/// Manages periodic background sync tasks and real-time sync
/// when the app is in the foreground.
///
/// Usage:
/// ```swift
/// let syncService = SyncService(tcpClient: client)
/// syncService.registerBackgroundTasks()
/// syncService.startForegroundSync()
/// ```
class SyncService: ObservableObject {
    // MARK: - Published State
    @Published var status: SyncStatus = .idle
    @Published var lastSyncResult: SyncResult?
    @Published var pendingItems: [SyncItem] = []
    @Published var lastSyncDate: Date?
    @Published var isConnected: Bool = false

    // MARK: - Dependencies
    private let tcpClient: TCPClient
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Sync Configuration
    private let syncInterval: TimeInterval = 300  // 5 minutes
    private let maxRetries = 3
    private var retryCount = 0

    // MARK: - Background Task
    private var backgroundTask: BGTask?
    private var foregroundTimer: Timer?

    // MARK: - Queues
    private let syncQueue = DispatchQueue(label: "com.edgeclaw.sync", qos: .utility)
    private let pendingItemsLock = NSLock()

    // MARK: - Callbacks
    var onConfigReceived: ((Data) -> Void)?
    var onStatusPushReceived: ((Data) -> Void)?
    var onPolicyUpdateReceived: ((Data) -> Void)?

    // MARK: - Init

    init(tcpClient: TCPClient) {
        self.tcpClient = tcpClient
        observeTCPState()
    }

    // MARK: - Background Task Registration

    /// Register background tasks with the system.
    /// Call this from `application(_:didFinishLaunchingWithOptions:)` or `@main` init.
    func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: kSyncTaskIdentifier,
            using: nil
        ) { [weak self] task in
            self?.handleBackgroundSync(task: task as! BGProcessingTask)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: kSyncRefreshIdentifier,
            using: nil
        ) { [weak self] task in
            self?.handleBackgroundRefresh(task: task as! BGAppRefreshTask)
        }

        scheduleBackgroundRefresh()
    }

    /// Schedule the next background refresh
    private func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: kSyncRefreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: syncInterval)

        do {
            try BGTaskScheduler.shared.submit(request)
            status = .scheduled
            print("[Sync] Background refresh scheduled in \(syncInterval)s")
        } catch {
            print("[Sync] Failed to schedule background refresh: \(error)")
        }
    }

    /// Schedule a longer background processing task
    private func scheduleBackgroundProcessing() {
        let request = BGProcessingTaskRequest(identifier: kSyncTaskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false

        do {
            try BGTaskScheduler.shared.submit(request)
            print("[Sync] Background processing task scheduled")
        } catch {
            print("[Sync] Failed to schedule processing: \(error)")
        }
    }

    // MARK: - Background Handlers

    private func handleBackgroundRefresh(task: BGAppRefreshTask) {
        scheduleBackgroundRefresh() // Schedule the next one

        task.expirationHandler = { [weak self] in
            self?.status = .idle
            task.setTaskCompleted(success: false)
        }

        performSync { [weak self] result in
            self?.lastSyncResult = result
            self?.lastSyncDate = Date()
            task.setTaskCompleted(success: result.errors.isEmpty)
        }
    }

    private func handleBackgroundSync(task: BGProcessingTask) {
        task.expirationHandler = { [weak self] in
            self?.status = .idle
            task.setTaskCompleted(success: false)
        }

        performFullSync { result in
            task.setTaskCompleted(success: result.errors.isEmpty)
        }
    }

    // MARK: - Foreground Sync

    /// Start periodic foreground sync
    func startForegroundSync() {
        stopForegroundSync()

        foregroundTimer = Timer.scheduledTimer(
            withTimeInterval: 30.0, // Check every 30s in foreground
            repeats: true
        ) { [weak self] _ in
            self?.syncIfNeeded()
        }

        // Immediate sync on start
        syncIfNeeded()
    }

    /// Stop foreground sync timer
    func stopForegroundSync() {
        foregroundTimer?.invalidate()
        foregroundTimer = nil
    }

    /// Trigger sync if there are pending items or last sync is stale
    func syncIfNeeded() {
        guard status != .syncing else { return }

        let needsSync: Bool
        if !pendingItems.isEmpty {
            needsSync = true
        } else if let lastSync = lastSyncDate {
            needsSync = Date().timeIntervalSince(lastSync) > syncInterval
        } else {
            needsSync = true
        }

        if needsSync {
            performSync { [weak self] result in
                DispatchQueue.main.async {
                    self?.lastSyncResult = result
                    self?.lastSyncDate = Date()
                }
            }
        }
    }

    // MARK: - Sync Operations

    /// Perform a standard sync (push pending items, pull updates)
    func performSync(completion: @escaping (SyncResult) -> Void) {
        guard isConnected else {
            let result = SyncResult(
                itemsSent: 0,
                itemsReceived: 0,
                errors: ["Not connected to desktop agent"],
                duration: 0,
                timestamp: Date()
            )
            completion(result)
            return
        }

        syncQueue.async { [weak self] in
            guard let self = self else { return }

            DispatchQueue.main.async {
                self.status = .syncing
            }

            let startTime = Date()
            var itemsSent = 0
            var itemsReceived = 0
            var errors: [String] = []

            // Push pending items
            let itemsToSend = self.getPendingItems()
            for item in itemsToSend {
                do {
                    try self.pushItem(item)
                    self.markItemSynced(item.id)
                    itemsSent += 1
                } catch {
                    errors.append("Push \(item.type.rawValue) failed: \(error.localizedDescription)")
                }
            }

            // Pull updates from desktop
            self.pullUpdates { received, pullErrors in
                itemsReceived = received
                errors.append(contentsOf: pullErrors)

                let duration = Date().timeIntervalSince(startTime)
                let result = SyncResult(
                    itemsSent: itemsSent,
                    itemsReceived: itemsReceived,
                    errors: errors,
                    duration: duration,
                    timestamp: Date()
                )

                DispatchQueue.main.async {
                    self.status = errors.isEmpty ? .completed : .failed
                    self.retryCount = errors.isEmpty ? 0 : self.retryCount + 1
                }

                completion(result)
            }
        }
    }

    /// Perform a full sync (all data types)
    func performFullSync(completion: @escaping (SyncResult) -> Void) {
        // Queue all sync item types
        enqueueAllSyncTypes()
        performSync(completion: completion)
    }

    // MARK: - Push / Pull

    private func pushItem(_ item: SyncItem) throws {
        // Encode as ECNP frame: type 0x10 (sync)
        let msgType: UInt8 = 0x10 // SyncData message type

        // Build payload: [item_type(1) | direction(1) | data_len(4) | data]
        var payload = Data()
        payload.append(itemTypeCode(item.type))
        payload.append(directionCode(item.direction))
        var len = UInt32(item.data.count).bigEndian
        payload.append(Data(bytes: &len, count: 4))
        payload.append(item.data)

        let semaphore = DispatchSemaphore(value: 0)
        var sendError: Error?

        tcpClient.sendECNP(msgType: msgType, payload: payload) { error in
            sendError = error
            semaphore.signal()
        }

        semaphore.wait()
        if let error = sendError {
            throw error
        }
    }

    private func pullUpdates(completion: @escaping (Int, [String]) -> Void) {
        // Request updates from desktop: type 0x11 (SyncRequest)
        let msgType: UInt8 = 0x11

        var payload = Data()
        // Include last sync timestamp
        let lastSync = lastSyncDate ?? Date.distantPast
        let timestamp = UInt64(lastSync.timeIntervalSince1970).bigEndian
        var ts = timestamp
        payload.append(Data(bytes: &ts, count: 8))

        tcpClient.sendECNP(msgType: msgType, payload: payload) { error in
            if let error = error {
                completion(0, ["Pull request failed: \(error.localizedDescription)"])
                return
            }
            // Response is handled via TCPClient's onFrameReceived callback
            completion(0, [])
        }
    }

    // MARK: - Item Management

    /// Enqueue an item for sync
    func enqueueItem(_ item: SyncItem) {
        pendingItemsLock.lock()
        pendingItems.append(item)
        pendingItemsLock.unlock()
    }

    /// Enqueue a config update for sync
    func enqueueConfigSync(configData: Data) {
        let item = SyncItem(
            id: UUID().uuidString,
            type: .config,
            data: configData,
            timestamp: Date(),
            direction: .push,
            synced: false
        )
        enqueueItem(item)
    }

    /// Enqueue a status push
    func enqueueStatusPush(statusData: Data) {
        let item = SyncItem(
            id: UUID().uuidString,
            type: .statusPush,
            data: statusData,
            timestamp: Date(),
            direction: .push,
            synced: false
        )
        enqueueItem(item)
    }

    private func enqueueAllSyncTypes() {
        // Queue empty pull requests for all sync types
        for type in [SyncItemType.config, .peerList, .sessionState, .policyUpdate] {
            let item = SyncItem(
                id: UUID().uuidString,
                type: type,
                data: Data(),
                timestamp: Date(),
                direction: .pull,
                synced: false
            )
            enqueueItem(item)
        }
    }

    private func getPendingItems() -> [SyncItem] {
        pendingItemsLock.lock()
        let items = pendingItems.filter { !$0.synced }
        pendingItemsLock.unlock()
        return items
    }

    private func markItemSynced(_ id: String) {
        pendingItemsLock.lock()
        if let index = pendingItems.firstIndex(where: { $0.id == id }) {
            pendingItems[index].synced = true
        }
        pendingItemsLock.unlock()

        // Clean up old synced items
        pendingItemsLock.lock()
        pendingItems.removeAll { $0.synced }
        pendingItemsLock.unlock()
    }

    // MARK: - Frame Processing

    /// Process incoming sync frames from the desktop
    func processIncomingFrame(_ frame: Data) {
        guard frame.count >= 6 else { return }

        let msgType = frame[1]

        switch msgType {
        case 0x12: // SyncResponse — config update from desktop
            let payload = frame.subdata(in: 6..<frame.count)
            handleSyncResponse(payload)

        case 0x13: // StatusPush — status update from desktop
            let payload = frame.subdata(in: 6..<frame.count)
            DispatchQueue.main.async { [weak self] in
                self?.onStatusPushReceived?(payload)
            }

        case 0x14: // PolicyUpdate — policy change from desktop
            let payload = frame.subdata(in: 6..<frame.count)
            DispatchQueue.main.async { [weak self] in
                self?.onPolicyUpdateReceived?(payload)
            }

        default:
            print("[Sync] Unknown sync frame type: 0x\(String(format: "%02x", msgType))")
        }
    }

    private func handleSyncResponse(_ payload: Data) {
        guard payload.count >= 2 else { return }

        let itemType = payload[0]

        switch itemType {
        case 0x01: // Config
            let configData = payload.subdata(in: 2..<payload.count)
            DispatchQueue.main.async { [weak self] in
                self?.onConfigReceived?(configData)
            }

        case 0x03: // PeerList
            // Apply received peer list
            break

        case 0x04: // SessionState
            // Apply received session states
            break

        case 0x05: // PolicyUpdate
            let policyData = payload.subdata(in: 2..<payload.count)
            DispatchQueue.main.async { [weak self] in
                self?.onPolicyUpdateReceived?(policyData)
            }

        default:
            break
        }
    }

    // MARK: - TCP State Observation

    private func observeTCPState() {
        tcpClient.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.isConnected = (state == .connected)
            }
            .store(in: &cancellables)

        // Set up frame handler for sync messages
        tcpClient.onFrameReceived = { [weak self] frame in
            self?.processIncomingFrame(frame)
        }
    }

    // MARK: - Helpers

    private func itemTypeCode(_ type: SyncItemType) -> UInt8 {
        switch type {
        case .config:       return 0x01
        case .identity:     return 0x02
        case .peerList:     return 0x03
        case .sessionState: return 0x04
        case .policyUpdate: return 0x05
        case .activityLog:  return 0x06
        case .statusPush:   return 0x07
        }
    }

    private func directionCode(_ direction: SyncDirection) -> UInt8 {
        switch direction {
        case .push:  return 0x01
        case .pull:  return 0x02
        case .bidir: return 0x03
        }
    }

    deinit {
        stopForegroundSync()
    }
}
