//
//  SessionsView.swift
//  EdgeClaw
//
//  Displays active encrypted sessions.
//

import SwiftUI

struct SessionsView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            Group {
                if appState.sessions.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "lock.shield")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("No Active Sessions")
                            .font(.headline)
                        Text("Connect to a peer to establish\nan encrypted session (AES-256-GCM).")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                } else {
                    List(appState.sessions) { session in
                        SessionRow(session: session)
                    }
                }
            }
            .navigationTitle("Sessions")
        }
    }
}

struct SessionRow: View {
    let session: ECSessionInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: stateIcon)
                    .foregroundColor(stateColor)
                Text("Session")
                    .font(.headline)
                Spacer()
                Text(session.state.capitalized)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(stateColor.opacity(0.15))
                    .cornerRadius(4)
            }

            LabeledContent("Peer") {
                Text(session.peerId)
                    .font(.caption)
                    .lineLimit(1)
            }

            HStack {
                Label("\(session.messagesSent) sent",
                      systemImage: "arrow.up.circle")
                    .font(.caption2)
                Spacer()
                Label("\(session.messagesReceived) recv",
                      systemImage: "arrow.down.circle")
                    .font(.caption2)
            }
            .foregroundColor(.secondary)

            Text("ID: \(session.sessionId)")
                .font(.system(.caption2, design: .monospaced))
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
        .padding(.vertical, 4)
    }

    private var stateIcon: String {
        switch session.state {
        case "established": return "lock.fill"
        case "initiating": return "lock.open"
        default: return "lock.slash"
        }
    }

    private var stateColor: Color {
        switch session.state {
        case "established": return .green
        case "initiating": return .orange
        default: return .red
        }
    }
}

#if DEBUG
struct SessionsView_Previews: PreviewProvider {
    static var previews: some View {
        SessionsView()
            .environmentObject(AppState())
    }
}
#endif
