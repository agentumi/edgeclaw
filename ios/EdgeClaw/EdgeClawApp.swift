//
//  EdgeClawApp.swift
//  EdgeClaw
//
//  EdgeClaw Mobile — Zero-Trust Edge AI Orchestration (iOS)
//  Copyright 2024-2026 EdgeClaw Authors. MIT OR Apache-2.0.
//

import SwiftUI
import BackgroundTasks

@main
struct EdgeClawApp: App {
    @StateObject private var appState = AppState()
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            BiometricAuthGate {
                ContentView()
                    .environmentObject(appState)
            }
            .onAppear {
                appState.initializeEngine()
            }
        }
    }
}

// MARK: - AppDelegate (APNs + Background Tasks)

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Register background tasks
        registerBackgroundTasks()

        // Request push notification permissions
        requestPushNotificationPermissions(application)

        return true
    }

    // MARK: - Background Tasks

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: kSyncTaskIdentifier,
            using: nil
        ) { task in
            self.handleBackgroundSync(task: task)
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: kSyncRefreshIdentifier,
            using: nil
        ) { task in
            self.handleBackgroundRefresh(task: task)
        }
    }

    private func handleBackgroundSync(task: BGTask) {
        // Schedule next sync
        scheduleBackgroundSync()

        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }

        // Perform lightweight sync
        DispatchQueue.global(qos: .utility).async {
            // TODO: Integrate with SyncService when engine is available
            task.setTaskCompleted(success: true)
        }
    }

    private func handleBackgroundRefresh(task: BGTask) {
        scheduleBackgroundRefresh()

        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }

        task.setTaskCompleted(success: true)
    }

    private func scheduleBackgroundSync() {
        let request = BGProcessingTaskRequest(identifier: kSyncTaskIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 600) // 10 min
        try? BGTaskScheduler.shared.submit(request)
    }

    private func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: kSyncRefreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 300) // 5 min
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: - Push Notifications (APNs)

    private func requestPushNotificationPermissions(_ application: UIApplication) {
        let center = UNUserNotificationCenter.current()
        center.delegate = self

        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
            if let error = error {
                print("[APNs] Authorization error: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - APNs Callbacks

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let tokenString = deviceToken.map { String(format: "%02x", $0) }.joined()
        print("[APNs] Device token: \(tokenString)")

        // Store token for server registration
        UserDefaults.standard.set(tokenString, forKey: "edgeclaw.apnsToken")

        // TODO: Send token to EdgeClaw Desktop agent for push support
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[APNs] Registration failed: \(error.localizedDescription)")
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Handle notification when app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }

    /// Handle notification tap
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo

        // Handle different notification types
        if let action = userInfo["action"] as? String {
            switch action {
            case "peer_discovered":
                // Navigate to Peers tab
                NotificationCenter.default.post(name: .navigateToPeers, object: nil)
            case "sync_complete":
                // Navigate to Dashboard
                NotificationCenter.default.post(name: .navigateToDashboard, object: nil)
            case "security_alert":
                // Navigate to Settings
                NotificationCenter.default.post(name: .navigateToSettings, object: nil)
            default:
                break
            }
        }

        completionHandler()
    }
}

// MARK: - Navigation Notifications

extension Notification.Name {
    static let navigateToPeers = Notification.Name("edgeclaw.navigateToPeers")
    static let navigateToDashboard = Notification.Name("edgeclaw.navigateToDashboard")
    static let navigateToSettings = Notification.Name("edgeclaw.navigateToSettings")
}

