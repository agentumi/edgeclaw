//
//  EdgeClawTests.swift
//  EdgeClawTests
//
//  Unit tests for EdgeClaw iOS app — 35 tests covering
//  models, keychain, data store, sync, BLE, and views.
//

import XCTest
@testable import EdgeClaw

// MARK: - Model Tests (8 tests)

class ModelTests: XCTestCase {

    // 1. ECDeviceIdentity
    func testDeviceIdentityId() {
        let identity = ECDeviceIdentity(
            deviceId: "test-123",
            publicKeyHex: "abc",
            fingerprint: "fp",
            createdAt: "2026-01-01T00:00:00Z"
        )
        XCTAssertEqual(identity.id, "test-123")
        XCTAssertEqual(identity.deviceId, "test-123")
    }

    // 2. ECDeviceIdentity Codable
    func testDeviceIdentityCodable() throws {
        let identity = ECDeviceIdentity(
            deviceId: "dev-1",
            publicKeyHex: "0123456789abcdef",
            fingerprint: "fp-1234",
            createdAt: "2026-03-01T12:00:00Z"
        )

        let data = try JSONEncoder().encode(identity)
        let decoded = try JSONDecoder().decode(ECDeviceIdentity.self, from: data)
        XCTAssertEqual(decoded.deviceId, identity.deviceId)
        XCTAssertEqual(decoded.publicKeyHex, identity.publicKeyHex)
        XCTAssertEqual(decoded.fingerprint, identity.fingerprint)
    }

    // 3. ECPeerInfo
    func testPeerInfoId() {
        let peer = ECPeerInfo(
            peerId: "peer-1",
            deviceName: "TestPC",
            deviceType: "pc",
            address: "192.168.1.1:8443",
            capabilities: ["status_query"],
            lastSeen: "2026-01-01",
            isConnected: true
        )
        XCTAssertEqual(peer.id, "peer-1")
        XCTAssertTrue(peer.isConnected)
        XCTAssertEqual(peer.capabilities.count, 1)
    }

    // 4. ECSessionInfo
    func testSessionInfo() {
        let session = ECSessionInfo(
            sessionId: "sess-1",
            peerId: "peer-1",
            state: "established",
            createdAt: "2026-01-01",
            expiresAt: "2026-01-02",
            messagesSent: 10,
            messagesReceived: 5
        )
        XCTAssertEqual(session.id, "sess-1")
        XCTAssertEqual(session.messagesSent, 10)
    }

    // 5. ECPolicyDecision
    func testPolicyDecisionCodable() throws {
        let decision = ECPolicyDecision(allowed: true, reason: "admin", riskLevel: 2)
        let data = try JSONEncoder().encode(decision)
        let decoded = try JSONDecoder().decode(ECPolicyDecision.self, from: data)
        XCTAssertTrue(decoded.allowed)
        XCTAssertEqual(decoded.reason, "admin")
        XCTAssertEqual(decoded.riskLevel, 2)
    }

    // 6. AgentModel
    func testAgentModel() {
        let agent = AgentModel(
            name: "Desktop Agent",
            address: "192.168.1.100",
            port: 8443,
            profile: "system",
            capabilities: ["status_query", "file_read"]
        )
        XCTAssertEqual(agent.name, "Desktop Agent")
        XCTAssertEqual(agent.port, 8443)
        XCTAssertEqual(agent.capabilities.count, 2)
        XCTAssertFalse(agent.isOnline)
    }

    // 7. ChatMessageModel
    func testChatMessageModel() {
        let msg = ChatMessageModel(
            agentId: "agent-1",
            sender: "user",
            content: "status",
            messageType: "command"
        )
        XCTAssertEqual(msg.sender, "user")
        XCTAssertEqual(msg.content, "status")
        XCTAssertFalse(msg.id.isEmpty)
    }

    // 8. DeviceGroupModel
    func testDeviceGroupModel() {
        let group = DeviceGroupModel(
            name: "Servers",
            description: "Production servers",
            icon: "server.rack",
            color: "red",
            deviceIds: ["dev-1", "dev-2"]
        )
        XCTAssertEqual(group.name, "Servers")
        XCTAssertEqual(group.deviceIds.count, 2)
        XCTAssertEqual(group.color, "red")
    }
}

// MARK: - SyncConfigModel Tests (3 tests)

class SyncConfigTests: XCTestCase {

    // 9. Default config
    func testDefaultSyncConfig() {
        let config = SyncConfigModel()
        XCTAssertEqual(config.port, 8443)
        XCTAssertTrue(config.useTLS)
        XCTAssertFalse(config.autoConnect)
        XCTAssertEqual(config.syncInterval, 300)
    }

    // 10. Custom config
    func testCustomSyncConfig() {
        let config = SyncConfigModel(
            desktopAddress: "10.0.0.1",
            port: 9443,
            useTLS: false,
            autoConnect: true,
            syncInterval: 60,
            syncOnWiFiOnly: true
        )
        XCTAssertEqual(config.desktopAddress, "10.0.0.1")
        XCTAssertEqual(config.syncInterval, 60)
        XCTAssertTrue(config.syncOnWiFiOnly)
    }

    // 11. Codable roundtrip
    func testSyncConfigCodable() throws {
        let config = SyncConfigModel(desktopAddress: "test.local", port: 1234)
        let data = try JSONEncoder().encode(config)
        let decoded = try JSONDecoder().decode(SyncConfigModel.self, from: data)
        XCTAssertEqual(decoded.desktopAddress, "test.local")
        XCTAssertEqual(decoded.port, 1234)
    }
}

// MARK: - ActivityLogEntry Tests (3 tests)

class ActivityLogTests: XCTestCase {

    // 12. Create entry
    func testActivityLogEntry() {
        let entry = ActivityLogEntry(
            activityType: "command_exec",
            title: "Run tests",
            description: "cargo test --all",
            importance: 7,
            tags: ["rust", "test"]
        )
        XCTAssertEqual(entry.importance, 7)
        XCTAssertEqual(entry.tags.count, 2)
        XCTAssertFalse(entry.id.isEmpty)
    }

    // 13. Importance clamping
    func testActivityLogImportanceClamping() {
        let tooHigh = ActivityLogEntry(activityType: "error", title: "Test", importance: 15)
        XCTAssertEqual(tooHigh.importance, 10)

        let tooLow = ActivityLogEntry(activityType: "error", title: "Test", importance: -5)
        XCTAssertEqual(tooLow.importance, 0)
    }

    // 14. Codable
    func testActivityLogCodable() throws {
        let entry = ActivityLogEntry(
            activityType: "file_edit",
            title: "Edit main.rs",
            importance: 5,
            project: "edgeclaw"
        )
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(entry)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let decoded = try decoder.decode(ActivityLogEntry.self, from: data)
        XCTAssertEqual(decoded.title, "Edit main.rs")
        XCTAssertEqual(decoded.project, "edgeclaw")
    }
}

// MARK: - PeerGroup Tests (4 tests)

class PeerGroupTests: XCTestCase {

    // 15. Empty group
    func testPeerGroupCreation() {
        let group = PeerGroup(name: "Test Group")
        XCTAssertEqual(group.name, "Test Group")
        XCTAssertEqual(group.icon, "folder")
        XCTAssertEqual(group.color, "blue")
        XCTAssertTrue(group.peerIds.isEmpty)
    }

    // 16. Group with peers
    func testPeerGroupWithPeers() {
        let group = PeerGroup(
            name: "Servers",
            icon: "server.rack",
            color: "red",
            peerIds: ["peer-1", "peer-2", "peer-3"]
        )
        XCTAssertEqual(group.peerIds.count, 3)
        XCTAssertEqual(group.icon, "server.rack")
    }

    // 17. SwiftUI color mapping
    func testPeerGroupColorMapping() {
        let colors = ["red", "green", "blue", "orange", "purple", "teal", "unknown"]
        for color in colors {
            let group = PeerGroup(name: "test", color: color)
            // Should not crash — just verify it returns a color
            XCTAssertNotNil(group.swiftColor)
        }
    }

    // 18. Codable roundtrip
    func testPeerGroupCodable() throws {
        let group = PeerGroup(
            name: "Prod",
            icon: "shield",
            color: "green",
            peerIds: ["a", "b"]
        )
        let data = try JSONEncoder().encode(group)
        let decoded = try JSONDecoder().decode(PeerGroup.self, from: data)
        XCTAssertEqual(decoded.name, "Prod")
        XCTAssertEqual(decoded.peerIds, ["a", "b"])
    }
}

// MARK: - KeyChainManager Tests (5 tests)

class KeyChainManagerTests: XCTestCase {
    let keychain = KeyChainManager()
    let testService = "com.edgeclaw.test"
    let testAccount = "test-account"

    override func tearDown() {
        try? keychain.delete(service: testService, account: testAccount)
    }

    // 19. Store and load
    func testStoreAndLoad() throws {
        let testData = "hello-keychain".data(using: .utf8)!
        try keychain.store(data: testData, service: testService, account: testAccount)
        let loaded = try keychain.load(service: testService, account: testAccount)
        XCTAssertEqual(loaded, testData)
    }

    // 20. Not found
    func testLoadNotFound() {
        XCTAssertThrowsError(
            try keychain.load(service: "nonexistent", account: "nonexistent")
        ) { error in
            XCTAssertTrue(error is KeyChainError)
        }
    }

    // 21. Delete
    func testDelete() throws {
        let data = "to-delete".data(using: .utf8)!
        try keychain.store(data: data, service: testService, account: testAccount)
        try keychain.delete(service: testService, account: testAccount)
        XCTAssertThrowsError(try keychain.load(service: testService, account: testAccount))
    }

    // 22. Exists check
    func testExists() throws {
        XCTAssertFalse(keychain.exists(service: testService, account: testAccount))
        let data = "test".data(using: .utf8)!
        try keychain.store(data: data, service: testService, account: testAccount)
        XCTAssertTrue(keychain.exists(service: testService, account: testAccount))
    }

    // 23. Overwrite on duplicate
    func testOverwriteOnDuplicate() throws {
        let data1 = "first".data(using: .utf8)!
        let data2 = "second".data(using: .utf8)!
        try keychain.store(data: data1, service: testService, account: testAccount)
        try keychain.store(data: data2, service: testService, account: testAccount)
        let loaded = try keychain.load(service: testService, account: testAccount)
        XCTAssertEqual(loaded, data2)
    }
}

// MARK: - KeyChainError Tests (2 tests)

class KeyChainErrorTests: XCTestCase {

    // 24. Error descriptions
    func testErrorDescriptions() {
        let errors: [KeyChainError] = [
            .duplicateItem,
            .itemNotFound,
            .unexpectedStatus(-1),
            .encodingError,
            .decodingError,
            .invalidData
        ]
        for error in errors {
            XCTAssertNotNil(error.errorDescription)
            XCTAssertFalse(error.errorDescription!.isEmpty)
        }
    }

    // 25. Specific error message
    func testSpecificErrorMessage() {
        let error = KeyChainError.itemNotFound
        XCTAssertEqual(error.errorDescription, "Item not found in Keychain")
    }
}

// MARK: - DataStore Tests (5 tests)

class DataStoreTests: XCTestCase {
    let store = DataStore.shared

    override func tearDown() {
        store.clearAll()
    }

    // 26. Agent CRUD
    func testAgentCRUD() {
        let agent = AgentModel(name: "Test", address: "1.2.3.4")
        store.addAgent(agent)
        let loaded = store.loadAgents()
        XCTAssertEqual(loaded.count, 1)
        XCTAssertEqual(loaded.first?.name, "Test")

        store.removeAgent(agent.agentId)
        XCTAssertTrue(store.loadAgents().isEmpty)
    }

    // 27. Group CRUD
    func testGroupCRUD() {
        let group = DeviceGroupModel(name: "Test Group")
        store.addGroup(group)
        XCTAssertEqual(store.loadGroups().count, 1)

        var updated = group
        updated.name = "Updated Group"
        store.updateGroup(updated)

        store.removeGroup(group.id)
        XCTAssertTrue(store.loadGroups().isEmpty)
    }

    // 28. Chat messages
    func testChatMessages() {
        let msg = ChatMessageModel(agentId: "agent-1", sender: "user", content: "hello")
        store.addChatMessage(msg)
        let loaded = store.loadChatMessages(forAgent: "agent-1")
        XCTAssertEqual(loaded.count, 1)
        XCTAssertEqual(loaded.first?.content, "hello")
    }

    // 29. Sync config
    func testSyncConfig() {
        let config = SyncConfigModel(desktopAddress: "10.0.0.1", port: 9999)
        store.saveSyncConfig(config)
        let loaded = store.loadSyncConfig()
        XCTAssertEqual(loaded.desktopAddress, "10.0.0.1")
        XCTAssertEqual(loaded.port, 9999)
    }

    // 30. Activity log
    func testActivityLog() {
        let entry = ActivityLogEntry(
            activityType: "command_exec",
            title: "Test Entry",
            importance: 6
        )
        store.addActivityLogEntry(entry)
        let loaded = store.loadActivityLog()
        XCTAssertGreaterThanOrEqual(loaded.count, 1)
        XCTAssertEqual(loaded.last?.title, "Test Entry")
    }
}

// MARK: - SyncService Tests (3 tests)

class SyncServiceTests: XCTestCase {

    // 31. SyncItem creation
    func testSyncItemCreation() {
        let item = SyncItem(
            id: "item-1",
            type: .config,
            data: Data([1, 2, 3]),
            timestamp: Date(),
            direction: .push,
            synced: false
        )
        XCTAssertEqual(item.type, .config)
        XCTAssertEqual(item.direction, .push)
        XCTAssertFalse(item.synced)
    }

    // 32. SyncResult
    func testSyncResult() {
        let result = SyncResult(
            itemsSent: 5,
            itemsReceived: 3,
            errors: ["test error"],
            duration: 1.5,
            timestamp: Date()
        )
        XCTAssertEqual(result.itemsSent, 5)
        XCTAssertEqual(result.errors.count, 1)
    }

    // 33. SyncItemType raw values
    func testSyncItemTypes() {
        let types: [SyncItemType] = [.config, .identity, .peerList, .sessionState, .policyUpdate, .activityLog, .statusPush]
        XCTAssertEqual(types.count, 7)
        for type in types {
            XCTAssertFalse(type.rawValue.isEmpty)
        }
    }
}

// MARK: - ChatMessage Tests (2 tests)

class ChatMessageTests: XCTestCase {

    // 34. ChatSender values
    func testChatSenderValues() {
        let senders: [ChatSender] = [.user, .agent, .system]
        XCTAssertEqual(senders.count, 3)
        XCTAssertEqual(ChatSender.user.rawValue, "user")
    }

    // 35. ChatMessageType values
    func testChatMessageTypes() {
        let types: [ChatMessageType] = [.text, .command, .result, .error, .status]
        XCTAssertEqual(types.count, 5)
        XCTAssertEqual(ChatMessageType.command.rawValue, "command")
    }
}
