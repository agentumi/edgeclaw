# EdgeClaw Mobile v1.0

[![CI](https://github.com/agentumi/edgeclaw/actions/workflows/ci.yml/badge.svg)](https://github.com/agentumi/edgeclaw/actions/workflows/ci.yml)

> Mobile-first Zero-Trust Edge AI Orchestration Platform

EdgeClaw Mobile은 스마트폰에서 BLE/WiFi/QUIC 기반으로 주변 PC·IoT 장비를 탐색·연결·자동화하는 Edge AI 플랫폼입니다.

## Features

- **Zero-Trust Security** — Ed25519 identity + X25519 ECDH key exchange + AES-256-GCM encryption
- **RBAC Policy Engine** — 4-tier roles (Viewer/Operator/Admin/Owner) with risk-level enforcement
- **BLE Device Discovery** — Bluetooth Low Energy scanning and connection management
- **ECNP v1.1 Protocol** — Binary framing codec for efficient edge communication
- **Material 3 UI** — Modern Jetpack Compose UI with dynamic color support
- **Cross-Platform Core** — Rust core library runs on Linux, macOS, Windows, and Android

## Architecture

```
┌──────────────────────────────────┐
│        EdgeClaw Core (Rust)      │
│──────────────────────────────────│
│ IdentityManager  │ SessionManager│
│ (Ed25519/X25519) │ (AES-256-GCM) │
│──────────────────┼───────────────│
│ PolicyEngine     │ PeerManager   │
│ (RBAC, Risk 0-3) │ (Discovery)   │
│──────────────────┼───────────────│
│ ECNP v1.1 Codec  │ Protocol Msgs │
│ (Binary Framing) │ (ECM/EAP/HB)  │
└──────────┬───────────────────────┘
           │ (Future: JNI/UniFFI)
┌──────────▼───────────────────────┐
│     Android App (Kotlin)         │
│──────────────────────────────────│
│ EdgeClawEngine  │ CryptoEngine   │
│ (Orchestrator)  │ (JCA AES-GCM)  │
│──────────────────┼───────────────│
│ BLE Scanner     │ PolicyEngine   │
│ (Discovery)     │ (Kotlin RBAC)   │
│──────────────────┼───────────────│
│ Material 3 UI   │ Navigation     │
│ (5 Screens)     │ (Compose Nav)   │
└──────────────────────────────────┘
```

## Project Structure

```
edgeclaw_mobile/
├── edgeclaw-core/              # Rust core library
│   ├── src/
│   │   ├── lib.rs              # Engine orchestrator (9 tests)
│   │   ├── identity.rs         # Ed25519/X25519 identity (4 tests)
│   │   ├── session.rs          # ECDH + AES-256-GCM sessions (5 tests)
│   │   ├── protocol.rs         # ECM/EAP/Heartbeat messages (4 tests)
│   │   ├── policy.rs           # RBAC policy engine (10 tests)
│   │   ├── peer.rs             # Peer management (6 tests)
│   │   ├── ecnp.rs             # ECNP v1.1 binary codec (8 tests)
│   │   └── error.rs            # Error types (1 test)
│   └── Cargo.toml
├── android/                    # Android application
│   ├── app/src/main/java/com/edgeclaw/mobile/
│   │   ├── EdgeClawApp.kt      # Application class
│   │   ├── core/
│   │   │   ├── model/Models.kt # Shared data models
│   │   │   ├── engine/EdgeClawEngine.kt
│   │   │   ├── crypto/CryptoEngine.kt
│   │   │   └── policy/PolicyEngine.kt
│   │   ├── ble/BleScanner.kt   # BLE discovery
│   │   ├── service/EdgeClawService.kt
│   │   └── ui/
│   │       ├── MainActivity.kt
│   │       ├── theme/Theme.kt
│   │       ├── navigation/Navigation.kt
│   │       └── screens/        # 5 Compose screens
│   ├── app/src/test/           # Unit tests (29 tests)
│   ├── build.gradle.kts
│   ├── gradlew / gradlew.bat
│   └── settings.gradle.kts
├── .github/workflows/ci.yml   # CI/CD pipeline
└── README.md
```

## Quick Start

### Prerequisites

- **Rust**: 1.75+ (`rustup` recommended)
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: 17+
- **Android SDK**: API 34, NDK 27+

### Build & Test Rust Core

```bash
cd edgeclaw-core

# Run all 47 tests
cargo test

# Build release
cargo build --release

# Lint
cargo clippy --all-targets -- -D warnings
```

### Build Android App

```bash
cd android

# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Lint check
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

## Security Model

| Role     | Max Risk | Capabilities |
|----------|----------|-------------|
| Viewer   | None (0) | status_query, heartbeat, peer_list |
| Operator | Low (1)  | + file_read, log_view, network_scan |
| Admin    | Medium (2)| + file_write, process_manage, config_edit |
| Owner    | High (3) | + shell_exec, firmware_update, system_reboot, security_config |

## Protocol: ECNP v1.1

Binary frame format:
```
┌─────────┬──────────┬────────────┬─────────────┐
│ Version │ Type     │ Length     │ Payload     │
│ (1B)    │ (1B)     │ (4B BE)   │ (N bytes)   │
└─────────┴──────────┴────────────┴─────────────┘
```

Message types: Handshake (0x01), Data (0x02), Control (0x03), Heartbeat (0x04), Error (0x05), Auth (0x06)

## CI/CD

GitHub Actions pipeline:
1. **Rust Core** — fmt check, clippy, build, test (Linux/macOS/Windows)
2. **Android Cross-Compile** — cargo-ndk for aarch64, armv7, x86_64
3. **Android App** — Gradle build, lint, unit tests, APK artifact
4. **Release** — Signed release APK on main branch push

## License

MIT OR Apache-2.0
