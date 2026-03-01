//
//  ChatView.swift
//  EdgeClaw
//
//  Chat interface for communicating with EdgeClaw Desktop agents.
//  Supports message bubbles, quick actions, and code block rendering.
//

import SwiftUI

/// Chat message model
struct ChatMessage: Identifiable {
    let id = UUID()
    let sender: ChatSender
    let content: String
    let timestamp: Date
    let type: ChatMessageType
    var isLoading: Bool = false
}

/// Who sent the message
enum ChatSender: String, Codable {
    case user
    case agent
    case system
}

/// Message content type
enum ChatMessageType: String, Codable {
    case text
    case command
    case result
    case error
    case status
}

/// Quick action for agent commands
struct QuickAction: Identifiable {
    let id = UUID()
    let title: String
    let icon: String
    let command: String
    let color: Color
}

struct ChatView: View {
    @EnvironmentObject var appState: AppState
    @State private var messageText = ""
    @State private var messages: [ChatMessage] = []
    @State private var selectedAgent: String = "default"
    @State private var isExecuting = false
    @State private var showQuickActions = true

    private let agents = ["default", "system", "security", "devops"]

    private let quickActions: [QuickAction] = [
        QuickAction(title: "Status", icon: "gauge.medium", command: "status", color: .blue),
        QuickAction(title: "Peers", icon: "antenna.radiowaves.left.and.right", command: "peer list", color: .green),
        QuickAction(title: "System Info", icon: "cpu", command: "info", color: .orange),
        QuickAction(title: "Health Check", icon: "heart.text.square", command: "health", color: .red),
        QuickAction(title: "Capabilities", icon: "list.bullet.rectangle", command: "capabilities", color: .purple),
        QuickAction(title: "Sessions", icon: "lock.shield", command: "session list", color: .teal),
        QuickAction(title: "Identity", icon: "person.badge.key", command: "identity", color: .indigo),
        QuickAction(title: "Network Scan", icon: "network", command: "network scan", color: .cyan),
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Agent selector
                agentSelector

                Divider()

                // Messages
                messagesScrollView

                // Quick actions
                if showQuickActions && messages.isEmpty {
                    quickActionsGrid
                }

                Divider()

                // Input bar
                inputBar
            }
            .navigationTitle("Agent Chat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        messages.removeAll()
                    } label: {
                        Image(systemName: "trash")
                    }
                    .disabled(messages.isEmpty)
                }
            }
            .onAppear {
                addSystemMessage("Connected to EdgeClaw agent (\(selectedAgent)). Ready for commands.")
            }
        }
    }

    // MARK: - Agent Selector

    private var agentSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(agents, id: \.self) { agent in
                    Button {
                        selectedAgent = agent
                        addSystemMessage("Switched to \(agent) agent.")
                    } label: {
                        Text(agent.capitalized)
                            .font(.caption)
                            .fontWeight(selectedAgent == agent ? .bold : .regular)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                selectedAgent == agent
                                    ? Color.blue.opacity(0.2)
                                    : Color.gray.opacity(0.1)
                            )
                            .foregroundColor(selectedAgent == agent ? .blue : .secondary)
                            .cornerRadius(16)
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Messages

    private var messagesScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(messages) { message in
                        messageBubble(message)
                            .id(message.id)
                    }
                }
                .padding()
            }
            .onChange(of: messages.count) { _ in
                if let lastMessage = messages.last {
                    withAnimation(.easeOut(duration: 0.3)) {
                        proxy.scrollTo(lastMessage.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private func messageBubble(_ message: ChatMessage) -> some View {
        HStack(alignment: .top, spacing: 8) {
            if message.sender == .agent || message.sender == .system {
                senderIcon(message.sender)
            }

            VStack(alignment: message.sender == .user ? .trailing : .leading, spacing: 4) {
                if message.type == .result || message.type == .command {
                    codeBlock(message.content, isError: message.type == .error)
                } else {
                    textBubble(message)
                }

                Text(formatTime(message.timestamp))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity,
                   alignment: message.sender == .user ? .trailing : .leading)

            if message.sender == .user {
                senderIcon(message.sender)
            }
        }
    }

    private func senderIcon(_ sender: ChatSender) -> some View {
        Image(systemName: iconForSender(sender))
            .font(.title3)
            .foregroundColor(colorForSender(sender))
            .frame(width: 32, height: 32)
            .background(colorForSender(sender).opacity(0.15))
            .cornerRadius(16)
    }

    private func textBubble(_ message: ChatMessage) -> some View {
        HStack {
            if message.isLoading {
                ProgressView()
                    .scaleEffect(0.8)
                    .padding(.trailing, 4)
            }

            Text(message.content)
                .font(.body)
                .foregroundColor(message.type == .error ? .red : .primary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(backgroundForSender(message.sender))
        .cornerRadius(12)
    }

    private func codeBlock(_ content: String, isError: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: isError ? "exclamationmark.triangle" : "terminal")
                    .font(.caption)
                Text(isError ? "Error" : "Output")
                    .font(.caption.bold())
                Spacer()
                Button {
                    copyToClipboard(content)
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.caption)
                }
            }
            .foregroundColor(isError ? .red : .green)

            Text(content)
                .font(.system(.caption, design: .monospaced))
                .foregroundColor(.primary)
                .textSelection(.enabled)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemGray6))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isError ? Color.red.opacity(0.3) : Color.green.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Quick Actions

    private var quickActionsGrid: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Quick Actions")
                .font(.headline)
                .foregroundColor(.secondary)
                .padding(.horizontal)

            LazyVGrid(
                columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ],
                spacing: 12
            ) {
                ForEach(quickActions) { action in
                    Button {
                        sendCommand(action.command)
                        showQuickActions = false
                    } label: {
                        VStack(spacing: 6) {
                            Image(systemName: action.icon)
                                .font(.title3)
                                .foregroundColor(action.color)
                            Text(action.title)
                                .font(.caption2)
                                .foregroundColor(.primary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(action.color.opacity(0.1))
                        .cornerRadius(12)
                    }
                    .disabled(isExecuting)
                }
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 8)
    }

    // MARK: - Input Bar

    private var inputBar: some View {
        HStack(spacing: 8) {
            Button {
                withAnimation {
                    showQuickActions.toggle()
                }
            } label: {
                Image(systemName: showQuickActions ? "xmark.circle" : "bolt.circle")
                    .font(.title2)
                    .foregroundColor(.blue)
            }

            TextField("Enter command…", text: $messageText)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.send)
                .onSubmit {
                    sendMessage()
                }
                .disabled(isExecuting)

            Button {
                sendMessage()
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.title2)
                    .foregroundColor(messageText.isEmpty ? .gray : .blue)
            }
            .disabled(messageText.isEmpty || isExecuting)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    // MARK: - Actions

    private func sendMessage() {
        let text = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        messageText = ""
        showQuickActions = false

        // Determine if it's a command (starts with / or known command)
        if text.hasPrefix("/") {
            sendCommand(String(text.dropFirst()))
        } else {
            sendCommand(text)
        }
    }

    private func sendCommand(_ command: String) {
        let userMsg = ChatMessage(
            sender: .user,
            content: command,
            timestamp: Date(),
            type: .command
        )
        messages.append(userMsg)

        // Show loading indicator
        let loadingMsg = ChatMessage(
            sender: .agent,
            content: "Executing…",
            timestamp: Date(),
            type: .text,
            isLoading: true
        )
        messages.append(loadingMsg)
        isExecuting = true

        // Simulate agent response (replace with real UniFFI call)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [self] in
            // Remove loading message
            messages.removeAll { $0.isLoading }

            let response = executeAgentCommand(command)
            messages.append(response)
            isExecuting = false
        }
    }

    private func executeAgentCommand(_ command: String) -> ChatMessage {
        // TODO: Replace with real UniFFI engine execution
        switch command.lowercased().trimmingCharacters(in: .whitespaces) {
        case "status":
            return ChatMessage(
                sender: .agent,
                content: """
                Engine: \(appState.isEngineReady ? "Ready" : "Not Ready")
                Peers: \(appState.peers.count)
                Sessions: \(appState.sessions.count)
                Sync: \(appState.isSyncConnected ? "Connected" : "Disconnected")
                Uptime: \(appState.uptimeSeconds)s
                """,
                timestamp: Date(),
                type: .result
            )

        case "peer list":
            if appState.peers.isEmpty {
                return ChatMessage(
                    sender: .agent,
                    content: "No peers discovered.",
                    timestamp: Date(),
                    type: .text
                )
            }
            let peerList = appState.peers.map {
                "  \($0.deviceName) [\($0.deviceType)] — \($0.address) (\($0.isConnected ? "online" : "offline"))"
            }.joined(separator: "\n")
            return ChatMessage(sender: .agent, content: peerList, timestamp: Date(), type: .result)

        case "identity":
            if let id = appState.identity {
                return ChatMessage(
                    sender: .agent,
                    content: """
                    Device ID: \(id.deviceId)
                    Fingerprint: \(id.fingerprint)
                    Public Key: \(id.publicKeyHex.prefix(32))…
                    Created: \(id.createdAt)
                    """,
                    timestamp: Date(),
                    type: .result
                )
            }
            return ChatMessage(sender: .agent, content: "No identity generated.", timestamp: Date(), type: .text)

        case "info":
            return ChatMessage(
                sender: .agent,
                content: """
                EdgeClaw Mobile v1.0.0
                Protocol: ECNP v1.1
                Encryption: AES-256-GCM
                Signing: Ed25519
                Key Exchange: X25519 ECDH
                Agent: \(selectedAgent)
                """,
                timestamp: Date(),
                type: .result
            )

        case "health":
            return ChatMessage(
                sender: .agent,
                content: """
                [PASS] Engine initialized
                [PASS] Cryptographic subsystem
                [PASS] Policy engine
                [\(appState.isSyncConnected ? "PASS" : "WARN")] Desktop sync
                [\(appState.peers.count > 0 ? "PASS" : "WARN")] Peer discovery
                """,
                timestamp: Date(),
                type: .result
            )

        case "capabilities":
            return ChatMessage(
                sender: .agent,
                content: """
                Available capabilities:
                  • status_query
                  • file_read / file_write
                  • process_manage
                  • log_read
                  • system_info
                  • network_scan
                  • ble_scan
                  • peer_manage
                """,
                timestamp: Date(),
                type: .result
            )

        case "session list":
            if appState.sessions.isEmpty {
                return ChatMessage(sender: .agent, content: "No active sessions.", timestamp: Date(), type: .text)
            }
            let list = appState.sessions.map {
                "  \($0.sessionId.prefix(8))… → \($0.peerId.prefix(8))… [\($0.state)]"
            }.joined(separator: "\n")
            return ChatMessage(sender: .agent, content: list, timestamp: Date(), type: .result)

        case "network scan":
            return ChatMessage(
                sender: .agent,
                content: "Network scan initiated. Results will appear when complete.",
                timestamp: Date(),
                type: .text
            )

        case "help":
            return ChatMessage(
                sender: .agent,
                content: """
                Available commands:
                  status        — Engine status overview
                  peer list     — List discovered peers
                  session list  — List active sessions
                  identity      — Show device identity
                  info          — System information
                  health        — Health check
                  capabilities  — List capabilities
                  network scan  — Scan local network
                  help          — This message
                """,
                timestamp: Date(),
                type: .result
            )

        default:
            return ChatMessage(
                sender: .agent,
                content: "Unknown command: '\(command)'. Type 'help' for available commands.",
                timestamp: Date(),
                type: .error
            )
        }
    }

    private func addSystemMessage(_ text: String) {
        let msg = ChatMessage(
            sender: .system,
            content: text,
            timestamp: Date(),
            type: .status
        )
        messages.append(msg)
    }

    // MARK: - Helpers

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    private func iconForSender(_ sender: ChatSender) -> String {
        switch sender {
        case .user:   return "person.fill"
        case .agent:  return "cpu"
        case .system: return "info.circle"
        }
    }

    private func colorForSender(_ sender: ChatSender) -> Color {
        switch sender {
        case .user:   return .blue
        case .agent:  return .green
        case .system: return .gray
        }
    }

    private func backgroundForSender(_ sender: ChatSender) -> Color {
        switch sender {
        case .user:   return Color.blue.opacity(0.15)
        case .agent:  return Color(.systemGray6)
        case .system: return Color.gray.opacity(0.1)
        }
    }

    private func copyToClipboard(_ text: String) {
        #if os(iOS)
        UIPasteboard.general.string = text
        #endif
    }
}

#if DEBUG
struct ChatView_Previews: PreviewProvider {
    static var previews: some View {
        ChatView()
            .environmentObject(AppState())
    }
}
#endif
