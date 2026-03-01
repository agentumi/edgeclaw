//
//  SettingsView.swift
//  EdgeClaw
//
//  App settings including sync configuration, security, and engine options.
//

import SwiftUI
import LocalAuthentication

struct SettingsView: View {
    @EnvironmentObject var appState: AppState

    @State private var desktopAddress = "192.168.1.100"
    @State private var desktopPort = "8443"
    @State private var deviceName = "iPhone"
    @State private var bleEnabled = true
    @State private var quicEnabled = false
    @State private var logLevel = "info"
    @State private var biometricEnabled = false
    @State private var biometricAvailable = false
    @State private var biometricType: LABiometryType = .none
    @State private var showBiometricAlert = false
    @State private var biometricAlertMessage = ""

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

                Section("Security") {
                    Toggle(biometricToggleLabel, isOn: $biometricEnabled)
                        .disabled(!biometricAvailable)
                        .onChange(of: biometricEnabled) { newValue in
                            if newValue {
                                authenticateBiometric()
                            } else {
                                UserDefaults.standard.set(false, forKey: "edgeclaw.biometricEnabled")
                            }
                        }

                    if !biometricAvailable {
                        Text("Biometric authentication is not available on this device.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

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
            .onAppear {
                checkBiometricAvailability()
                biometricEnabled = UserDefaults.standard.bool(forKey: "edgeclaw.biometricEnabled")
            }
            .alert("Biometric Auth", isPresented: $showBiometricAlert) {
                Button("OK") {
                    biometricEnabled = false
                }
            } message: {
                Text(biometricAlertMessage)
            }
        }
    }

    // MARK: - Biometric Authentication

    private var biometricToggleLabel: String {
        switch biometricType {
        case .faceID:
            return "Face ID Lock"
        case .touchID:
            return "Touch ID Lock"
        case .opticID:
            return "Optic ID Lock"
        @unknown default:
            return "Biometric Lock"
        }
    }

    private func checkBiometricAvailability() {
        let context = LAContext()
        var error: NSError?
        biometricAvailable = context.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics, error: &error
        )
        biometricType = context.biometryType
    }

    private func authenticateBiometric() {
        let context = LAContext()
        context.localizedCancelTitle = "Cancel"

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Authenticate to enable biometric lock for EdgeClaw"
        ) { success, error in
            DispatchQueue.main.async {
                if success {
                    biometricEnabled = true
                    UserDefaults.standard.set(true, forKey: "edgeclaw.biometricEnabled")
                } else {
                    biometricEnabled = false
                    if let error = error {
                        biometricAlertMessage = "Authentication failed: \(error.localizedDescription)"
                        showBiometricAlert = true
                    }
                }
            }
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
