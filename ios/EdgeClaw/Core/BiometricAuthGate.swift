//
//  BiometricAuthGate.swift
//  EdgeClaw
//
//  Biometric authentication gate — requires Face ID / Touch ID on app launch.
//  Wraps the main ContentView and blocks access until authenticated.
//

import SwiftUI
import LocalAuthentication

/// Biometric authentication gate view.
///
/// Wrap around `ContentView` to require Face ID/Touch ID before
/// granting access to the app. Enabled/disabled via settings.
///
/// Usage:
/// ```swift
/// // In EdgeClawApp.swift:
/// BiometricAuthGate {
///     ContentView()
///         .environmentObject(appState)
/// }
/// ```
struct BiometricAuthGate<Content: View>: View {
    @ViewBuilder let content: () -> Content

    @State private var isAuthenticated = false
    @State private var isAuthenticating = false
    @State private var authError: String?
    @State private var showFallback = false

    private var biometricEnabled: Bool {
        UserDefaults.standard.bool(forKey: "edgeclaw.biometricEnabled")
    }

    var body: some View {
        Group {
            if !biometricEnabled || isAuthenticated {
                content()
            } else {
                lockScreen
            }
        }
        .onAppear {
            if biometricEnabled && !isAuthenticated {
                authenticate()
            }
        }
    }

    // MARK: - Lock Screen

    private var lockScreen: some View {
        VStack(spacing: 24) {
            Spacer()

            // App icon
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 64))
                .foregroundColor(.blue)

            Text("EdgeClaw")
                .font(.largeTitle.bold())

            Text("Authentication Required")
                .font(.headline)
                .foregroundColor(.secondary)

            // Biometric type info
            Text(biometricDescription)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            if let error = authError {
                Text(error)
                    .font(.caption)
                    .foregroundColor(.red)
                    .padding(.horizontal, 20)
            }

            // Authenticate button
            Button {
                authenticate()
            } label: {
                HStack {
                    Image(systemName: biometricIcon)
                    Text("Authenticate")
                }
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            .padding(.horizontal, 40)
            .disabled(isAuthenticating)

            if isAuthenticating {
                ProgressView("Authenticating…")
                    .padding()
            }

            Spacer()

            // Version info
            Text("EdgeClaw Mobile v1.0.0")
                .font(.caption2)
                .foregroundColor(.secondary)
                .padding(.bottom, 20)
        }
        .background(Color(.systemBackground))
    }

    // MARK: - Authentication

    private func authenticate() {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            // Fallback to device passcode
            authenticateWithPasscode()
            return
        }

        isAuthenticating = true
        authError = nil

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Authenticate to access EdgeClaw"
        ) { success, authenticationError in
            DispatchQueue.main.async {
                isAuthenticating = false
                if success {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isAuthenticated = true
                    }
                } else {
                    if let error = authenticationError as? LAError {
                        switch error.code {
                        case .userCancel:
                            authError = "Authentication cancelled"
                        case .userFallback:
                            authenticateWithPasscode()
                        case .biometryLockout:
                            authError = "Biometric locked out. Use device passcode."
                            authenticateWithPasscode()
                        case .biometryNotAvailable:
                            authError = "Biometric not available"
                        default:
                            authError = "Authentication failed"
                        }
                    } else {
                        authError = "Authentication failed"
                    }
                }
            }
        }
    }

    private func authenticateWithPasscode() {
        let context = LAContext()

        isAuthenticating = true
        authError = nil

        context.evaluatePolicy(
            .deviceOwnerAuthentication, // Includes passcode fallback
            localizedReason: "Authenticate with device passcode to access EdgeClaw"
        ) { success, error in
            DispatchQueue.main.async {
                isAuthenticating = false
                if success {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isAuthenticated = true
                    }
                } else {
                    authError = "Passcode authentication failed"
                }
            }
        }
    }

    // MARK: - Helpers

    private var biometricIcon: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID:   return "faceid"
        case .touchID:  return "touchid"
        case .opticID:  return "opticid"
        @unknown default: return "lock"
        }
    }

    private var biometricDescription: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID:
            return "Use Face ID to unlock EdgeClaw and access your secure edge devices."
        case .touchID:
            return "Use Touch ID to unlock EdgeClaw and access your secure edge devices."
        case .opticID:
            return "Use Optic ID to unlock EdgeClaw and access your secure edge devices."
        @unknown default:
            return "Authenticate to unlock EdgeClaw and access your secure edge devices."
        }
    }
}

#if DEBUG
struct BiometricAuthGate_Previews: PreviewProvider {
    static var previews: some View {
        BiometricAuthGate {
            Text("App Content")
        }
    }
}
#endif
