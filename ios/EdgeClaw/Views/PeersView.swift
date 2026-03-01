//
//  PeersView.swift
//  EdgeClaw
//
//  Displays discovered peers and allows connecting to them.
//  Includes peer group management (CRUD).
//

import SwiftUI

/// Peer group for organizing devices
struct PeerGroup: Identifiable, Codable {
    let id: String
    var name: String
    var icon: String
    var color: String
    var peerIds: [String]
    var createdAt: Date

    init(id: String = UUID().uuidString, name: String, icon: String = "folder",
         color: String = "blue", peerIds: [String] = [], createdAt: Date = Date()) {
        self.id = id
        self.name = name
        self.icon = icon
        self.color = color
        self.peerIds = peerIds
        self.createdAt = createdAt
    }

    var swiftColor: Color {
        switch color {
        case "red": return .red
        case "green": return .green
        case "blue": return .blue
        case "orange": return .orange
        case "purple": return .purple
        case "teal": return .teal
        default: return .blue
        }
    }
}

struct PeersView: View {
    @EnvironmentObject var appState: AppState
    @State private var showAddPeer = false
    @State private var isScanning = false
    @State private var showGroupManagement = false
    @State private var peerGroups: [PeerGroup] = []
    @State private var selectedGroupId: String?
    @State private var showCreateGroup = false
    @State private var showEditGroup = false
    @State private var editingGroup: PeerGroup?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Group filter pills
                if !peerGroups.isEmpty {
                    groupFilterBar
                    Divider()
                }

                // Peer list or empty state
                Group {
                    if filteredPeers.isEmpty {
                        emptyState
                    } else {
                        peerList
                    }
                }
            }
            .navigationTitle("Peers")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button {
                            showAddPeer = true
                        } label: {
                            Label("Add Peer", systemImage: "plus.circle")
                        }
                        Button {
                            showCreateGroup = true
                        } label: {
                            Label("Create Group", systemImage: "folder.badge.plus")
                        }
                        Button {
                            showGroupManagement = true
                        } label: {
                            Label("Manage Groups", systemImage: "folder.badge.gearshape")
                        }
                    } label: {
                        Image(systemName: "plus.circle")
                    }
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        isScanning.toggle()
                    } label: {
                        Label(
                            isScanning ? "Stop Scan" : "BLE Scan",
                            systemImage: isScanning
                                ? "antenna.radiowaves.left.and.right.slash"
                                : "antenna.radiowaves.left.and.right"
                        )
                    }
                }
            }
            .sheet(isPresented: $showAddPeer) {
                AddPeerSheet()
            }
            .sheet(isPresented: $showCreateGroup) {
                CreateGroupSheet(groups: $peerGroups, peers: appState.peers)
            }
            .sheet(isPresented: $showGroupManagement) {
                GroupManagementView(groups: $peerGroups, peers: appState.peers)
            }
            .sheet(item: $editingGroup) { group in
                EditGroupSheet(groups: $peerGroups, group: group, peers: appState.peers)
            }
            .onAppear {
                loadGroups()
            }
        }
    }

    private var filteredPeers: [ECPeerInfo] {
        guard let groupId = selectedGroupId,
              let group = peerGroups.first(where: { $0.id == groupId }) else {
            return appState.peers
        }
        return appState.peers.filter { group.peerIds.contains($0.peerId) }
    }

    // MARK: - Group Filter Bar

    private var groupFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // All peers pill
                Button {
                    selectedGroupId = nil
                } label: {
                    Text("All (\(appState.peers.count))")
                        .font(.caption)
                        .fontWeight(selectedGroupId == nil ? .bold : .regular)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(selectedGroupId == nil ? Color.blue.opacity(0.2) : Color.gray.opacity(0.1))
                        .foregroundColor(selectedGroupId == nil ? .blue : .secondary)
                        .cornerRadius(16)
                }

                ForEach(peerGroups) { group in
                    Button {
                        selectedGroupId = (selectedGroupId == group.id) ? nil : group.id
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: group.icon)
                                .font(.caption2)
                            Text("\(group.name) (\(group.peerIds.count))")
                                .font(.caption)
                                .fontWeight(selectedGroupId == group.id ? .bold : .regular)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            selectedGroupId == group.id
                                ? group.swiftColor.opacity(0.2)
                                : Color.gray.opacity(0.1)
                        )
                        .foregroundColor(selectedGroupId == group.id ? group.swiftColor : .secondary)
                        .cornerRadius(16)
                    }
                    .contextMenu {
                        Button {
                            editingGroup = group
                        } label: {
                            Label("Edit Group", systemImage: "pencil")
                        }
                        Button(role: .destructive) {
                            deleteGroup(group.id)
                        } label: {
                            Label("Delete Group", systemImage: "trash")
                        }
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("No Peers Discovered")
                .font(.headline)
            Text("Start a BLE scan or add a peer manually.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }

    private var peerList: some View {
        List {
            ForEach(filteredPeers) { peer in
                PeerRow(peer: peer, groups: peerGroups)
                    .contextMenu {
                        ForEach(peerGroups) { group in
                            let isInGroup = group.peerIds.contains(peer.peerId)
                            Button {
                                togglePeerInGroup(peerId: peer.peerId, groupId: group.id)
                            } label: {
                                Label(
                                    isInGroup ? "Remove from \(group.name)" : "Add to \(group.name)",
                                    systemImage: isInGroup ? "folder.badge.minus" : "folder.badge.plus"
                                )
                            }
                        }
                    }
            }
            .onDelete { indexSet in
                let peersToDelete = indexSet.map { filteredPeers[$0] }
                for peer in peersToDelete {
                    appState.removePeer(peerId: peer.peerId)
                }
            }
        }
    }

    // MARK: - Group Persistence

    private func loadGroups() {
        if let data = UserDefaults.standard.data(forKey: "edgeclaw.peerGroups"),
           let decoded = try? JSONDecoder().decode([PeerGroup].self, from: data) {
            peerGroups = decoded
        }
    }

    private func saveGroups() {
        if let data = try? JSONEncoder().encode(peerGroups) {
            UserDefaults.standard.set(data, forKey: "edgeclaw.peerGroups")
        }
    }

    private func deleteGroup(_ groupId: String) {
        peerGroups.removeAll { $0.id == groupId }
        if selectedGroupId == groupId { selectedGroupId = nil }
        saveGroups()
    }

    private func togglePeerInGroup(peerId: String, groupId: String) {
        guard let idx = peerGroups.firstIndex(where: { $0.id == groupId }) else { return }
        if peerGroups[idx].peerIds.contains(peerId) {
            peerGroups[idx].peerIds.removeAll { $0 == peerId }
        } else {
            peerGroups[idx].peerIds.append(peerId)
        }
        saveGroups()
    }
}

struct PeerRow: View {
    let peer: ECPeerInfo
    var groups: [PeerGroup] = []

    var body: some View {
        HStack {
            Image(systemName: iconForType(peer.deviceType))
                .foregroundColor(peer.isConnected ? .green : .gray)
                .font(.title2)

            VStack(alignment: .leading, spacing: 2) {
                Text(peer.deviceName)
                    .font(.headline)
                Text(peer.address)
                    .font(.caption)
                    .foregroundColor(.secondary)
                if !peer.capabilities.isEmpty {
                    Text(peer.capabilities.joined(separator: ", "))
                        .font(.caption2)
                        .foregroundColor(.blue)
                }
                // Show group badges
                let peerGroups = groups.filter { $0.peerIds.contains(peer.peerId) }
                if !peerGroups.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(peerGroups) { group in
                            Text(group.name)
                                .font(.caption2)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(group.swiftColor.opacity(0.15))
                                .foregroundColor(group.swiftColor)
                                .cornerRadius(4)
                        }
                    }
                }
            }

            Spacer()

            Circle()
                .fill(peer.isConnected ? Color.green : Color.gray.opacity(0.3))
                .frame(width: 10, height: 10)
        }
        .padding(.vertical, 4)
    }

    private func iconForType(_ type: String) -> String {
        switch type.lowercased() {
        case "pc", "desktop": return "desktopcomputer"
        case "smartphone", "phone": return "iphone"
        case "tablet": return "ipad"
        case "server": return "server.rack"
        default: return "cpu"
        }
    }
}

struct AddPeerSheet: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss

    @State private var peerId = ""
    @State private var name = ""
    @State private var address = ""
    @State private var deviceType = "pc"

    let deviceTypes = ["pc", "smartphone", "tablet", "server"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Peer Details") {
                    TextField("Peer ID", text: $peerId)
                    TextField("Device Name", text: $name)
                    TextField("Address (IP:port)", text: $address)
                    Picker("Device Type", selection: $deviceType) {
                        ForEach(deviceTypes, id: \.self) { type in
                            Text(type.capitalized).tag(type)
                        }
                    }
                }
            }
            .navigationTitle("Add Peer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        appState.addPeer(
                            peerId: peerId.isEmpty ? UUID().uuidString : peerId,
                            name: name,
                            type: deviceType,
                            address: address,
                            capabilities: []
                        )
                        dismiss()
                    }
                    .disabled(name.isEmpty || address.isEmpty)
                }
            }
        }
    }
}

#if DEBUG
struct PeersView_Previews: PreviewProvider {
    static var previews: some View {
        PeersView()
            .environmentObject(AppState())
    }
}
#endif

// MARK: - Create Group Sheet

struct CreateGroupSheet: View {
    @Binding var groups: [PeerGroup]
    let peers: [ECPeerInfo]
    @Environment(\.dismiss) var dismiss

    @State private var groupName = ""
    @State private var selectedIcon = "folder"
    @State private var selectedColor = "blue"
    @State private var selectedPeerIds: Set<String> = []

    let icons = ["folder", "server.rack", "desktopcomputer", "iphone", "network", "shield", "star"]
    let colors = ["blue", "green", "red", "orange", "purple", "teal"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Group Info") {
                    TextField("Group Name", text: $groupName)

                    Picker("Icon", selection: $selectedIcon) {
                        ForEach(icons, id: \.self) { icon in
                            Label(icon, systemImage: icon).tag(icon)
                        }
                    }

                    Picker("Color", selection: $selectedColor) {
                        ForEach(colors, id: \.self) { color in
                            Text(color.capitalized).tag(color)
                        }
                    }
                }

                Section("Members (\(selectedPeerIds.count))") {
                    ForEach(peers) { peer in
                        Button {
                            if selectedPeerIds.contains(peer.peerId) {
                                selectedPeerIds.remove(peer.peerId)
                            } else {
                                selectedPeerIds.insert(peer.peerId)
                            }
                        } label: {
                            HStack {
                                Text(peer.deviceName)
                                    .foregroundColor(.primary)
                                Spacer()
                                if selectedPeerIds.contains(peer.peerId) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.blue)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Create Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        let group = PeerGroup(
                            name: groupName,
                            icon: selectedIcon,
                            color: selectedColor,
                            peerIds: Array(selectedPeerIds)
                        )
                        groups.append(group)
                        saveGroups()
                        dismiss()
                    }
                    .disabled(groupName.isEmpty)
                }
            }
        }
    }

    private func saveGroups() {
        if let data = try? JSONEncoder().encode(groups) {
            UserDefaults.standard.set(data, forKey: "edgeclaw.peerGroups")
        }
    }
}

// MARK: - Edit Group Sheet

struct EditGroupSheet: View {
    @Binding var groups: [PeerGroup]
    let group: PeerGroup
    let peers: [ECPeerInfo]
    @Environment(\.dismiss) var dismiss

    @State private var groupName: String
    @State private var selectedIcon: String
    @State private var selectedColor: String
    @State private var selectedPeerIds: Set<String>

    let icons = ["folder", "server.rack", "desktopcomputer", "iphone", "network", "shield", "star"]
    let colors = ["blue", "green", "red", "orange", "purple", "teal"]

    init(groups: Binding<[PeerGroup]>, group: PeerGroup, peers: [ECPeerInfo]) {
        self._groups = groups
        self.group = group
        self.peers = peers
        self._groupName = State(initialValue: group.name)
        self._selectedIcon = State(initialValue: group.icon)
        self._selectedColor = State(initialValue: group.color)
        self._selectedPeerIds = State(initialValue: Set(group.peerIds))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Group Info") {
                    TextField("Group Name", text: $groupName)

                    Picker("Icon", selection: $selectedIcon) {
                        ForEach(icons, id: \.self) { icon in
                            Label(icon, systemImage: icon).tag(icon)
                        }
                    }

                    Picker("Color", selection: $selectedColor) {
                        ForEach(colors, id: \.self) { color in
                            Text(color.capitalized).tag(color)
                        }
                    }
                }

                Section("Members (\(selectedPeerIds.count))") {
                    ForEach(peers) { peer in
                        Button {
                            if selectedPeerIds.contains(peer.peerId) {
                                selectedPeerIds.remove(peer.peerId)
                            } else {
                                selectedPeerIds.insert(peer.peerId)
                            }
                        } label: {
                            HStack {
                                Text(peer.deviceName)
                                    .foregroundColor(.primary)
                                Spacer()
                                if selectedPeerIds.contains(peer.peerId) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.blue)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Edit Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let idx = groups.firstIndex(where: { $0.id == group.id }) {
                            groups[idx].name = groupName
                            groups[idx].icon = selectedIcon
                            groups[idx].color = selectedColor
                            groups[idx].peerIds = Array(selectedPeerIds)
                        }
                        saveGroups()
                        dismiss()
                    }
                    .disabled(groupName.isEmpty)
                }
            }
        }
    }

    private func saveGroups() {
        if let data = try? JSONEncoder().encode(groups) {
            UserDefaults.standard.set(data, forKey: "edgeclaw.peerGroups")
        }
    }
}

// MARK: - Group Management View

struct GroupManagementView: View {
    @Binding var groups: [PeerGroup]
    let peers: [ECPeerInfo]
    @Environment(\.dismiss) var dismiss
    @State private var editingGroup: PeerGroup?

    var body: some View {
        NavigationStack {
            List {
                if groups.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "folder.badge.questionmark")
                            .font(.largeTitle)
                            .foregroundColor(.secondary)
                        Text("No Groups")
                            .font(.headline)
                        Text("Create groups to organize your peers.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                } else {
                    ForEach(groups) { group in
                        HStack {
                            Image(systemName: group.icon)
                                .foregroundColor(group.swiftColor)
                                .font(.title2)
                                .frame(width: 32)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(group.name)
                                    .font(.headline)
                                Text("\(group.peerIds.count) peer(s)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            Button {
                                editingGroup = group
                            } label: {
                                Image(systemName: "pencil.circle")
                                    .foregroundColor(.blue)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .onDelete { indexSet in
                        groups.remove(atOffsets: indexSet)
                        saveGroups()
                    }
                    .onMove { from, to in
                        groups.move(fromOffsets: from, toOffset: to)
                        saveGroups()
                    }
                }
            }
            .navigationTitle("Manage Groups")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
                if !groups.isEmpty {
                    ToolbarItem(placement: .navigationBarLeading) {
                        EditButton()
                    }
                }
            }
            .sheet(item: $editingGroup) { group in
                EditGroupSheet(groups: $groups, group: group, peers: peers)
            }
        }
    }

    private func saveGroups() {
        if let data = try? JSONEncoder().encode(groups) {
            UserDefaults.standard.set(data, forKey: "edgeclaw.peerGroups")
        }
    }
}
