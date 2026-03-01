//
//  PeersView.swift
//  EdgeClaw
//
//  Displays discovered peers and allows connecting to them.
//

import SwiftUI

struct PeersView: View {
    @EnvironmentObject var appState: AppState
    @State private var showAddPeer = false
    @State private var isScanning = false

    var body: some View {
        NavigationStack {
            Group {
                if appState.peers.isEmpty {
                    emptyState
                } else {
                    peerList
                }
            }
            .navigationTitle("Peers")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showAddPeer = true
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
            ForEach(appState.peers) { peer in
                PeerRow(peer: peer)
            }
            .onDelete { indexSet in
                for i in indexSet {
                    appState.removePeer(peerId: appState.peers[i].peerId)
                }
            }
        }
    }
}

struct PeerRow: View {
    let peer: ECPeerInfo

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
