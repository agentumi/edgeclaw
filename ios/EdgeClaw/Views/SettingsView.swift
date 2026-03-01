//
//  SettingsView.swift
//  EdgeClaw
//
//  App settings including sync configuration and engine options.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var appState: AppState

    @State private var desktopAddress = "192.168.1.100"
    @State private var desktopPort = "8443"
    @State private var deviceName = "iPhone"
    @State private var bleEnabled = true
    @State private var quicEnabled = false
    @State private var logLevel = "info"

    let logLevels = ["error", "warn", "info", "debug", "trace"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Device") {
                    TextField("Device Name", text: $deviceName)
                    Toggle("BLE Discovery", isOn: $bleEnabled)
                    Toggle("QUIC Transport", isOn: $quicEnabled)
                    Picker("Log Level", selection: $logLevel) {
                        ForEach(logLevels, id: \.self) { level in
                            Text(level).tag(level)
                        }
                    }
                }

                Section("Desktop Sync") {
                    TextField("Desktop Address", text: $desktopAddress)
                        .keyboardType(.decimalPad)
                    TextField("Port", text: $desktopPort)
                        .keyboardType(.numberPad)

                    HStack {
                        Circle()
                            .fill(appState.isSyncConnected ? Color.green : Color.gray)
                            .frame(width: 10, height: 10)
                        Text(appState.isSyncConnected ? "Connected" : "Disconnected")
                            .font(.subheadline)
                    }

                    Button("Connect to Desktop") {
                        let port = UInt16(desktopPort) ?? 8443
                        appState.initSync(desktopAddress: desktopAddress, port: port)
                    }
                    .disabled(desktopAddress.isEmpty)

                    if appState.isSyncConnected {
                        Button("Disconnect", role: .destructive) {
                            appState.syncShutdown()
                        }
                    }
                }

                Section("Security") {
                    LabeledContent("Encryption") {
                        Text("AES-256-GCM")
                    }
                    LabeledContent("Signing") {
                        Text("Ed25519")
                    }
                    LabeledContent("Key Exchange") {
                        Text("X25519 ECDH")
                    }
                    LabeledContent("Protocol") {
                        Text("ECNP v1.1")
                    }
                }

                Section("About") {
                    LabeledContent("Version") {
                        Text("1.0.0")
                    }
                    LabeledContent("Core") {
                        Text("edgeclaw-core (Rust)")
                    }
                    LabeledContent("License") {
                        Text("MIT / Apache-2.0")
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }
}

#if DEBUG
struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
            .environmentObject(AppState())
    }
}
#endif
