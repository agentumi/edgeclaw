<p align="center">
  <img src="https://img.shields.io/badge/EdgeClaw-Mobile-blue?style=for-the-badge&logo=android&logoColor=white" alt="EdgeClaw Mobile" />
</p>

<h1 align="center">EdgeClaw Mobile</h1>

<p align="center">
  <strong>Zero-Trust Edge AI Orchestration — Mobile First</strong>
</p>

<p align="center">
  <a href="https://github.com/agentumi/edgeclaw_mobile/actions/workflows/ci.yml"><img src="https://github.com/agentumi/edgeclaw_mobile/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/version-1.0.0-blue" alt="Version" />
  <img src="https://img.shields.io/badge/license-MIT%20%7C%20Apache--2.0-green" alt="License" />
  <img src="https://img.shields.io/badge/rust-1.75%2B-orange?logo=rust" alt="Rust" />
  <img src="https://img.shields.io/badge/kotlin-1.9%2B-purple?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/API-34%2B-brightgreen?logo=android" alt="Android API" />
  <img src="https://img.shields.io/badge/tests-82%20passed-success" alt="Tests" />
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-architecture">Architecture</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-security-model">Security</a> •
  <a href="#-project-structure">Structure</a> •
  <a href="#-testing">Testing</a> •
  <a href="#-contributing">Contributing</a>
</p>

---

> **EdgeClaw Mobile** turns your smartphone into a zero-trust edge AI orchestrator.
> Discover, authenticate, and automate nearby PCs & IoT devices over BLE/WiFi/QUIC —
> all with military-grade encryption and fine-grained RBAC policies.

## ✨ Features

| Category | Feature | Details |
|----------|---------|---------|
| 🔐 **Security** | Zero-Trust Auth | Ed25519 device identity + X25519 ECDH key exchange |
| 🛡️ **Encryption** | AES-256-GCM | End-to-end encryption with replay protection |
| 👤 **Access Control** | 4-Tier RBAC | Viewer → Operator → Admin → Owner with risk levels |
| 📡 **Discovery** | BLE Scanner | Automatic Bluetooth Low Energy device discovery |
| 📦 **Protocol** | ECNP v1.1 | Binary framing codec for efficient edge communication |
| 🎨 **UI** | Material 3 | Jetpack Compose with dynamic color & 5 screens |
| 🦀 **Core** | Rust Library | Cross-platform core (Linux, macOS, Windows, Android) |
| 🔄 **CI/CD** | GitHub Actions | Automated build, test, lint for Rust + Android |

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Android App Layer                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Material 3  │  │  BLE Scanner │  │  EdgeClaw     │  │
│  │  Compose UI  │  │  (Discovery) │  │  Service      │  │
│  │  (5 Screens) │  │              │  │  (Background) │  │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘  │
│         │                 │                   │          │
│  ┌──────▼─────────────────▼───────────────────▼───────┐  │
│  │              EdgeClawEngine (Kotlin)                │  │
│  │         CryptoEngine  │  PolicyEngine              │  │
│  └────────────────────────┬───────────────────────────┘  │
├────────────────────────────┼─────────────────────────────┤
│                    JNI / UniFFI Bridge                   │
├────────────────────────────┼─────────────────────────────┤
│                  EdgeClaw Core (Rust)                    │
│  ┌──────────────┐  ┌──────▼───────┐  ┌───────────────┐  │
│  │  Identity     │  │  Session     │  │  Policy       │  │
│  │  (Ed25519/    │  │  (ECDH +     │  │  (RBAC 4-tier │  │
│  │   X25519)     │  │   AES-GCM)   │  │   + Risk)     │  │
│  ├──────────────┤  ├──────────────┤  ├───────────────┤  │
│  │  ECNP v1.1   │  │  Protocol    │  │  Peer         │  │
│  │  (Binary      │  │  (ECM/EAP/   │  │  (Discovery   │  │
│  │   Codec)      │  │   Heartbeat) │  │   + Tracking) │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Rust | 1.75+ | [rustup.rs](https://rustup.rs/) |
| Android Studio | Hedgehog+ | [developer.android.com](https://developer.android.com/studio) |
| JDK | 17+ | Included with Android Studio |
| Android SDK | API 34 | SDK Manager |
| Android NDK | 27+ | SDK Manager |

### Build & Run

```bash
# 1. Clone
git clone https://github.com/agentumi/edgeclaw_mobile.git
cd edgeclaw_mobile

# 2. Build & test Rust core (47 tests)
cd edgeclaw-core
cargo build --release
cargo test

# 3. Build & test Android app (29 tests)
cd ../android
./gradlew assembleDebug
./gradlew test

# 4. Lint everything
cargo clippy --all-targets -- -D warnings
cargo fmt --check
./gradlew lint
```

### Cross-Compile for Android

```bash
# Install Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Install cargo-ndk
cargo install cargo-ndk

# Build for ARM64
cargo ndk -t aarch64-linux-android build --release
```

### iOS (SwiftUI)

```bash
# Prerequisites (in macOS VM or native Mac)
rustup target add x86_64-apple-ios aarch64-apple-ios aarch64-apple-ios-sim
cargo install uniffi-bindgen

# Build Rust core for iOS Simulator
cd edgeclaw-core
cargo build --target x86_64-apple-ios --release

# Generate UniFFI Swift bindings
cd ../ios
chmod +x generate-bindings.sh
./generate-bindings.sh

# Open in Xcode
# See ios/IOS_QUICKSTART.md for full setup guide
```

## 🔐 Security Model

### RBAC Roles & Risk Levels

| Role | Risk Level | Capabilities |
|------|-----------|-------------|
| **Viewer** | None (0) | `status_query`, `heartbeat`, `peer_list` |
| **Operator** | Low (1) | + `file_read`, `log_view`, `network_scan` |
| **Admin** | Medium (2) | + `file_write`, `process_manage`, `config_edit` |
| **Owner** | High (3) | + `shell_exec`, `firmware_update`, `system_reboot`, `security_config` |

### Cryptography Stack

```
Device Identity ──── Ed25519 (signing + verification)
        │
Key Exchange ─────── X25519 ECDH (ephemeral)
        │
Key Derivation ───── HKDF-SHA256 (info: "ecnp-session-v1")
        │
Message Encrypt ──── AES-256-GCM (12-byte random nonce)
        │
Anti-Replay ──────── Per-device nonce tracking + timestamp ±30s
```

### Protocol: ECNP v1.1

```
┌─────────┬──────────┬────────────┬─────────────┐
│ Version │ Type     │ Length     │ Payload     │
│ (1B)    │ (1B)     │ (4B BE)   │ (N bytes)   │
└─────────┴──────────┴────────────┴─────────────┘
```

| Type | Code | Direction |
|------|------|-----------|
| Handshake | `0x01` | Bidirectional |
| Data | `0x02` | Bidirectional |
| Control | `0x03` | Bidirectional |
| Heartbeat | `0x04` | Bidirectional |
| Error | `0x05` | Bidirectional |
| Auth | `0x06` | Device → Gateway |

## 📁 Project Structure

```
edgeclaw_mobile/
├── edgeclaw-core/                    # Rust core library (82 tests)
│   ├── src/
│   │   ├── lib.rs                    # Engine orchestrator (9 tests)
│   │   ├── identity.rs               # Ed25519/X25519 identity (4 tests)
│   │   ├── session.rs                # ECDH + AES-256-GCM (5 tests)
│   │   ├── protocol.rs               # ECM/EAP/Heartbeat (4 tests)
│   │   ├── policy.rs                 # RBAC policy engine (10 tests)
│   │   ├── peer.rs                   # Peer management (6 tests)
│   │   ├── ecnp.rs                   # ECNP v1.1 codec (8 tests)
│   │   ├── sync.rs                   # Desktop sync (26 tests)
│   │   ├── uniffi_bridge.rs          # UniFFI iOS/Kotlin FFI (9 tests)
│   │   ├── edgeclaw.udl              # UniFFI interface definition
│   │   └── error.rs                  # Error types (1 test)
│   └── Cargo.toml
│
├── android/                          # Android application (29 tests)
│   ├── app/src/
│   │   ├── main/java/com/edgeclaw/mobile/
│   │   │   ├── EdgeClawApp.kt        # Application entry point
│   │   │   ├── core/
│   │   │   │   ├── model/Models.kt   # Shared data models
│   │   │   │   ├── engine/EdgeClawEngine.kt
│   │   │   │   ├── crypto/CryptoEngine.kt
│   │   │   │   └── policy/PolicyEngine.kt
│   │   │   ├── ble/BleScanner.kt     # BLE discovery
│   │   │   ├── service/EdgeClawService.kt
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       ├── theme/Theme.kt
│   │   │       ├── navigation/Navigation.kt
│   │   │       └── screens/          # 5 Compose screens
│   │   └── test/                     # Unit tests
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── ios/                              # iOS application (SwiftUI)
│   ├── EdgeClaw/
│   │   ├── EdgeClawApp.swift         # App entry point
│   │   ├── ContentView.swift         # Tab navigation
│   │   ├── Info.plist                # App configuration
│   │   ├── Core/
│   │   │   └── AppState.swift        # Global state (ObservableObject)
│   │   ├── Views/
│   │   │   ├── DashboardView.swift   # Engine status dashboard
│   │   │   ├── PeersView.swift       # Peer discovery list
│   │   │   ├── SessionsView.swift    # Encrypted sessions
│   │   │   ├── IdentityView.swift    # Device identity
│   │   │   └── SettingsView.swift    # Settings & sync config
│   │   ├── BLE/
│   │   │   └── BLEScanner.swift      # CoreBluetooth scanner
│   │   ├── Network/
│   │   │   └── TCPClient.swift       # NWConnection TCP client
│   │   └── Generated/               # UniFFI Swift bindings
│   ├── build-rust.sh                 # iOS Rust build script
│   ├── generate-bindings.sh          # UniFFI bindgen script
│   └── IOS_QUICKSTART.md            # iOS setup guide
│
├── .github/workflows/ci.yml          # CI/CD pipeline
├── AGENTS.md                         # AI agent guidelines
├── CLAUDE.md                         # Claude AI guidelines
├── CONTRIBUTING.md                   # Contribution guide
├── SECURITY.md                       # Security policy
├── CHANGELOG.md                      # Release history
├── CODE_OF_CONDUCT.md                # Community standards
├── LICENSE-MIT                       # MIT License
├── LICENSE-APACHE                    # Apache 2.0 License
└── NOTICE                            # Third-party attributions
```

## 🧪 Testing

### Test Summary

| Component | Tests | Command |
|-----------|-------|---------|
| **Rust Core** | 82 | `cargo test` |
| **Android App** | 29 | `./gradlew test` |
| **Total** | **111** | — |

### Rust Core — Module Tests

```bash
cargo test identity::tests     # Ed25519/X25519 keypairs (4 tests)
cargo test session::tests      # ECDH + AES-256-GCM (5 tests)
cargo test policy::tests       # RBAC authorization (10 tests)
cargo test protocol::tests     # Message encode/decode (4 tests)
cargo test peer::tests         # Peer discovery (6 tests)
cargo test ecnp::tests         # Binary codec (8 tests)
cargo test lib::tests          # Engine integration (9 tests)
cargo test error::tests        # Error handling (1 test)
```

### Android — Unit Tests

```bash
./gradlew test                                    # All 29 tests
./gradlew test --tests com.edgeclaw.mobile.core.* # Core tests only
./gradlew test --info                             # Verbose output
```

### Lint & Format

```bash
# Rust
cargo clippy --all-targets -- -D warnings   # Zero warnings policy
cargo fmt --check                            # Format check

# Android
./gradlew lint                               # Android lint
```

## 🤝 CI/CD Pipeline

| Stage | Platform | Actions |
|-------|----------|---------|
| **Rust Core** | Linux / macOS / Windows | fmt, clippy, build, test |
| **Cross-Compile** | Android | cargo-ndk (aarch64, armv7, x86_64) |
| **Android App** | Ubuntu | Gradle build, lint, unit tests |
| **Release** | Ubuntu | Signed APK artifact on `main` push |

## 🤝 Contributing

We welcome contributions! Please read:

- [CONTRIBUTING.md](CONTRIBUTING.md) — Development workflow & PR process
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — Community standards
- [SECURITY.md](SECURITY.md) — Vulnerability reporting

## 📜 License

Dual-licensed under **MIT** or **Apache-2.0** at your option.

- [LICENSE-MIT](LICENSE-MIT)
- [LICENSE-APACHE](LICENSE-APACHE)

Copyright (c) 2025-2026 EdgeClaw Contributors.

---

<p align="center">
  <sub>Built with 🦀 Rust + 💜 Kotlin — Part of the <a href="https://github.com/agentumi">EdgeClaw</a> ecosystem</sub>
</p>
