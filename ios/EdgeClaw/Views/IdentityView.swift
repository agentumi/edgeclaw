//
//  IdentityView.swift
//  EdgeClaw
//
//  Displays the device's cryptographic identity (Ed25519).
//

import SwiftUI

struct IdentityView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let identity = appState.identity {
                        identityDetails(identity)
                    } else {
                        noIdentityView
                    }
                }
                .padding()
            }
            .navigationTitle("Identity")
        }
    }

    private var noIdentityView: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.badge.key")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text("No Identity Generated")
                .font(.headline)
            Text("Generate a new Ed25519 + X25519 keypair\nto authenticate with the edge mesh.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button("Generate Identity") {
                appState.generateIdentity()
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func identityDetails(_ id: ECDeviceIdentity) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            // Fingerprint hero
            VStack(spacing: 8) {
                Image(systemName: "person.badge.shield.checkmark")
                    .font(.system(size: 48))
                    .foregroundColor(.green)
                Text(id.fingerprint)
                    .font(.system(.title2, design: .monospaced))
                Text("Device Fingerprint")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(.regularMaterial)
            .cornerRadius(12)

            // Details
            GroupBox("Details") {
                VStack(alignment: .leading, spacing: 8) {
                    DetailRow(label: "Device ID", value: id.deviceId)
                    Divider()
                    DetailRow(label: "Public Key (Ed25519)", value: id.publicKeyHex)
                    Divider()
                    DetailRow(label: "Created", value: id.createdAt)
                }
            }

            // Security info
            GroupBox("Security") {
                VStack(alignment: .leading, spacing: 6) {
                    SecurityRow(icon: "checkmark.shield", text: "Ed25519 Signing Key", ok: true)
                    SecurityRow(icon: "checkmark.shield", text: "X25519 Key Exchange", ok: true)
                    SecurityRow(icon: "checkmark.shield", text: "AES-256-GCM Encryption", ok: true)
                    SecurityRow(icon: "checkmark.shield", text: "Zero-Trust Policy Engine", ok: true)
                }
            }

            // Regenerate
            Button(role: .destructive) {
                appState.generateIdentity()
            } label: {
                Label("Regenerate Identity", systemImage: "arrow.counterclockwise")
            }
            .frame(maxWidth: .infinity)
        }
    }
}

struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(.caption, design: .monospaced))
                .lineLimit(2)
                .textSelection(.enabled)
        }
    }
}

struct SecurityRow: View {
    let icon: String
    let text: String
    let ok: Bool

    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(ok ? .green : .red)
            Text(text)
                .font(.subheadline)
        }
    }
}

#if DEBUG
struct IdentityView_Previews: PreviewProvider {
    static var previews: some View {
        IdentityView()
            .environmentObject(AppState())
    }
}
#endif
