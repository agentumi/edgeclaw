//
//  ContentView.swift
//  EdgeClaw
//
//  Main tab navigation for EdgeClaw iOS app.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        TabView {
            DashboardView()
                .tabItem {
                    Label("Dashboard", systemImage: "gauge.medium")
                }

            PeersView()
                .tabItem {
                    Label("Peers", systemImage: "antenna.radiowaves.left.and.right")
                }

            SessionsView()
                .tabItem {
                    Label("Sessions", systemImage: "lock.shield")
                }

            IdentityView()
                .tabItem {
                    Label("Identity", systemImage: "person.badge.key")
                }

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
        }
        .tint(.blue)
    }
}

#if DEBUG
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(AppState())
    }
}
#endif
