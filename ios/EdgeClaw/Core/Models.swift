//
//  Models.swift
//  EdgeClaw
//
//  Swift Data models for persistent storage.
//  Uses Codable + UserDefaults/file storage for data persistence.
//

import Foundation

// MARK: - Agent Model

/// Represents a connected EdgeClaw Desktop agent
struct AgentModel: Identifiable, Codable {
    var id: String { agentId }
    let agentId: String
    var name: String
    var address: String
    var port: UInt16
    var profile: String        // system, security, devops, etc.
    var version: String
    var capabilities: [String]
    var isOnline: Bool
    var lastSeen: Date
    var createdAt: Date

    init(agentId: String = UUID().uuidString,
         name: String,
         address: String,
         port: UInt16 = 8443,
         profile: String = "system",
         version: String = "3.0.0",
         capabilities: [String] = [],
         isOnline: Bool = false) {
        self.agentId = agentId
        self.name = name
        self.address = address
        self.port = port
        self.profile = profile
        self.version = version
        self.capabilities = capabilities
        self.isOnline = isOnline
        self.lastSeen = Date()
        self.createdAt = Date()
    }
}

// MARK: - Chat Message Model

/// Persistent chat message for agent conversations
struct ChatMessageModel: Identifiable, Codable {
    let id: String
    let agentId: String
    let sender: String    // "user", "agent", "system"
    let content: String
    let messageType: String // "text", "command", "result", "error", "status"
    let timestamp: Date
    let metadata: [String: String]?

    init(agentId: String,
         sender: String,
         content: String,
         messageType: String = "text",
         metadata: [String: String]? = nil) {
        self.id = UUID().uuidString
        self.agentId = agentId
        self.sender = sender
        self.content = content
        self.messageType = messageType
        self.timestamp = Date()
        self.metadata = metadata
    }
}

// MARK: - Device Group Model

/// Persistent device group for peer organization
struct DeviceGroupModel: Identifiable, Codable {
    let id: String
    var name: String
    var description: String
    var icon: String
    var color: String
    var deviceIds: [String]
    var createdAt: Date
    var updatedAt: Date

    init(name: String,
         description: String = "",
         icon: String = "folder",
         color: String = "blue",
         deviceIds: [String] = []) {
        self.id = UUID().uuidString
        self.name = name
        self.description = description
        self.icon = icon
        self.color = color
        self.deviceIds = deviceIds
        self.createdAt = Date()
        self.updatedAt = Date()
    }
}

// MARK: - Sync Configuration Model

/// Configuration for desktop sync
struct SyncConfigModel: Codable {
    var desktopAddress: String
    var port: UInt16
    var useTLS: Bool
    var autoConnect: Bool
    var syncInterval: Int      // seconds
    var syncOnWiFiOnly: Bool

    init(desktopAddress: String = "192.168.1.100",
         port: UInt16 = 8443,
         useTLS: Bool = true,
         autoConnect: Bool = false,
         syncInterval: Int = 300,
         syncOnWiFiOnly: Bool = false) {
        self.desktopAddress = desktopAddress
        self.port = port
        self.useTLS = useTLS
        self.autoConnect = autoConnect
        self.syncInterval = syncInterval
        self.syncOnWiFiOnly = syncOnWiFiOnly
    }
}

// MARK: - Activity Log Entry Model

/// Local activity log entry for tracking user/agent actions
struct ActivityLogEntry: Identifiable, Codable {
    let id: String
    let activityType: String    // "file_edit", "command_exec", "ai_chat", "decision", "error"
    let title: String
    let description: String
    let importance: Int         // 0-10
    let agentId: String?
    let project: String?
    let tags: [String]
    let timestamp: Date

    init(activityType: String,
         title: String,
         description: String = "",
         importance: Int = 5,
         agentId: String? = nil,
         project: String? = nil,
         tags: [String] = []) {
        self.id = UUID().uuidString
        self.activityType = activityType
        self.title = title
        self.description = description
        self.importance = min(10, max(0, importance))
        self.agentId = agentId
        self.project = project
        self.tags = tags
        self.timestamp = Date()
    }
}

// MARK: - Data Store

/// Lightweight persistent data store using UserDefaults + file storage.
///
/// Manages CRUD operations for all model types.
class DataStore {
    static let shared = DataStore()

    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    // Keys
    private let agentsKey = "edgeclaw.agents"
    private let chatMessagesKey = "edgeclaw.chatMessages"
    private let groupsKey = "edgeclaw.deviceGroups"
    private let syncConfigKey = "edgeclaw.syncConfig"
    private let activityLogKey = "edgeclaw.activityLog"

    private init() {
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    // MARK: - Agents

    func saveAgents(_ agents: [AgentModel]) {
        if let data = try? encoder.encode(agents) {
            defaults.set(data, forKey: agentsKey)
        }
    }

    func loadAgents() -> [AgentModel] {
        guard let data = defaults.data(forKey: agentsKey),
              let agents = try? decoder.decode([AgentModel].self, from: data) else {
            return []
        }
        return agents
    }

    func addAgent(_ agent: AgentModel) {
        var agents = loadAgents()
        agents.append(agent)
        saveAgents(agents)
    }

    func removeAgent(_ agentId: String) {
        var agents = loadAgents()
        agents.removeAll { $0.agentId == agentId }
        saveAgents(agents)
    }

    // MARK: - Chat Messages

    func saveChatMessages(_ messages: [ChatMessageModel], forAgent agentId: String) {
        let key = "\(chatMessagesKey).\(agentId)"
        if let data = try? encoder.encode(messages) {
            defaults.set(data, forKey: key)
        }
    }

    func loadChatMessages(forAgent agentId: String) -> [ChatMessageModel] {
        let key = "\(chatMessagesKey).\(agentId)"
        guard let data = defaults.data(forKey: key),
              let messages = try? decoder.decode([ChatMessageModel].self, from: data) else {
            return []
        }
        return messages
    }

    func addChatMessage(_ message: ChatMessageModel) {
        var messages = loadChatMessages(forAgent: message.agentId)
        messages.append(message)
        // Keep last 500 messages per agent
        if messages.count > 500 {
            messages = Array(messages.suffix(500))
        }
        saveChatMessages(messages, forAgent: message.agentId)
    }

    // MARK: - Device Groups

    func saveGroups(_ groups: [DeviceGroupModel]) {
        if let data = try? encoder.encode(groups) {
            defaults.set(data, forKey: groupsKey)
        }
    }

    func loadGroups() -> [DeviceGroupModel] {
        guard let data = defaults.data(forKey: groupsKey),
              let groups = try? decoder.decode([DeviceGroupModel].self, from: data) else {
            return []
        }
        return groups
    }

    func addGroup(_ group: DeviceGroupModel) {
        var groups = loadGroups()
        groups.append(group)
        saveGroups(groups)
    }

    func updateGroup(_ group: DeviceGroupModel) {
        var groups = loadGroups()
        if let idx = groups.firstIndex(where: { $0.id == group.id }) {
            groups[idx] = group
        }
        saveGroups(groups)
    }

    func removeGroup(_ groupId: String) {
        var groups = loadGroups()
        groups.removeAll { $0.id == groupId }
        saveGroups(groups)
    }

    // MARK: - Sync Config

    func saveSyncConfig(_ config: SyncConfigModel) {
        if let data = try? encoder.encode(config) {
            defaults.set(data, forKey: syncConfigKey)
        }
    }

    func loadSyncConfig() -> SyncConfigModel {
        guard let data = defaults.data(forKey: syncConfigKey),
              let config = try? decoder.decode(SyncConfigModel.self, from: data) else {
            return SyncConfigModel()
        }
        return config
    }

    // MARK: - Activity Log

    func saveActivityLog(_ entries: [ActivityLogEntry]) {
        // Use file storage for potentially large logs
        let url = activityLogFileURL
        if let data = try? encoder.encode(entries) {
            try? data.write(to: url)
        }
    }

    func loadActivityLog() -> [ActivityLogEntry] {
        let url = activityLogFileURL
        guard let data = try? Data(contentsOf: url),
              let entries = try? decoder.decode([ActivityLogEntry].self, from: data) else {
            return []
        }
        return entries
    }

    func addActivityLogEntry(_ entry: ActivityLogEntry) {
        var entries = loadActivityLog()
        entries.append(entry)
        // Keep last 10000 entries
        if entries.count > 10000 {
            entries = Array(entries.suffix(10000))
        }
        saveActivityLog(entries)
    }

    private var activityLogFileURL: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return dir.appendingPathComponent("edgeclaw_activity.json")
    }

    // MARK: - Cleanup

    func clearAll() {
        defaults.removeObject(forKey: agentsKey)
        defaults.removeObject(forKey: groupsKey)
        defaults.removeObject(forKey: syncConfigKey)
        try? FileManager.default.removeItem(at: activityLogFileURL)
    }
}
