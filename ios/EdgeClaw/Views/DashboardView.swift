//
//  DashboardView.swift
//  EdgeClaw
//
//  Main dashboard showing engine status, identity, and quick stats.
//

import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Status card
                    StatusCard(
                        isReady: appState.isEngineReady,
                        status: appState.statusMessage,
                        uptime: appState.uptimeSeconds
                    )

                    // Identity card
                    if let identity = appState.identity {
                        IdentityCard(identity: identity)
                    } else {
                        Button("Generate Identity") {
                            appState.generateIdentity()
                        }
                        .buttonStyle(.borderedProminent)
                    }

                    // Quick stats
                    HStack(spacing: 16) {
                        StatBadge(
                            title: "Peers",
                            value: "\(appState.peers.count)",
                            icon: "antenna.radiowaves.left.and.right",
                            color: .blue
                        )
                        StatBadge(
                            title: "Sessions",
                            value: "\(appState.sessions.count)",
                            icon: "lock.shield",
                            color: .green
                        )
                        StatBadge(
                            title: "Sync",
                            value: appState.isSyncConnected ? "ON" : "OFF",
                            icon: "arrow.triangle.2.circlepath",
                            color: appState.isSyncConnected ? .green : .gray
                        )
                    }

                    // Error banner
                    if let error = appState.errorMessage {
                        ErrorBanner(message: error) {
                            appState.errorMessage = nil
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("EdgeClaw")
        }
    }
}

// MARK: - Sub-views

struct StatusCard: View {
    let isReady: Bool
    let status: String
    let uptime: UInt64

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Circle()
                    .fill(isReady ? Color.green : Color.red)
                    .frame(width: 12, height: 12)
                Text("Engine")
                    .font(.headline)
                Spacer()
                Text(formatUptime(uptime))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Text(status)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }

    private func formatUptime(_ secs: UInt64) -> String {
        let h = secs / 3600
        let m = (secs % 3600) / 60
        let s = secs % 60
        return String(format: "%02d:%02d:%02d", h, m, s)
    }
}

struct IdentityCard: View {
    let identity: ECDeviceIdentity

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Device Identity")
                .font(.headline)
            LabeledContent("Device ID") {
                Text(identity.deviceId)
                    .font(.caption)
                    .lineLimit(1)
            }
            LabeledContent("Fingerprint") {
                Text(identity.fingerprint)
                    .font(.system(.caption, design: .monospaced))
            }
            LabeledContent("Created") {
                Text(identity.createdAt)
                    .font(.caption)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }
}

struct StatBadge: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
            Text(value)
                .font(.title3.bold())
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(.regularMaterial)
        .cornerRadius(12)
    }
}

struct ErrorBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle")
                .foregroundColor(.red)
            Text(message)
                .font(.caption)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark.circle")
            }
        }
        .padding()
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
    }
}

#if DEBUG
struct DashboardView_Previews: PreviewProvider {
    static var previews: some View {
        DashboardView()
            .environmentObject(AppState())
    }
}
#endif
