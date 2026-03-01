//
//  EdgeClawApp.swift
//  EdgeClaw
//
//  EdgeClaw Mobile — Zero-Trust Edge AI Orchestration (iOS)
//  Copyright 2024-2026 EdgeClaw Authors. MIT OR Apache-2.0.
//

import SwiftUI

@main
struct EdgeClawApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .onAppear {
                    appState.initializeEngine()
                }
        }
    }
}
