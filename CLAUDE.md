# CLAUDE.md

> This file provides guidance to Claude (Anthropic AI) when working with EdgeClaw Mobile.

## Quick Facts

| Aspect | Details |
|--------|---------|
| Language | Rust (core) + Kotlin (Android) + Swift (iOS) |
| Tests | 82 (Rust) + 29 (Kotlin) = 111 total |
| Minimum | Rust 1.75, Android API 34, iOS 16.0 |
| Protocol | ECNP v1.1 binary framing |
| Security | Ed25519 + X25519 + AES-256-GCM |

## Project Structure

```
edgeclaw-core/                 # Rust library
├── src/
│   ├── lib.rs                # Public API (9 tests)
│   ├── identity.rs           # Ed25519/X25519 (4 tests)
│   ├── session.rs            # ECDH + AES-256-GCM (5 tests)
│   ├── protocol.rs           # ECM/EAP/Heartbeat (4 tests)
│   ├── policy.rs             # RBAC engine (10 tests)
│   ├── peer.rs               # Peer management (6 tests)
│   ├── ecnp.rs               # Binary codec (8 tests)
│   ├── sync.rs               # Desktop sync (26 tests)
│   ├── uniffi_bridge.rs      # UniFFI FFI bridge (9 tests)
│   ├── edgeclaw.udl          # UniFFI interface
│   └── error.rs              # Error types (1 test)
└── Cargo.toml

android/                       # Kotlin app
├── app/src/
│   ├── main/java/com/edgeclaw/mobile/
│   │   ├── EdgeClawApp.kt    # Application class
│   │   ├── core/             # Engine, crypto, policy
│   │   ├── ble/              # BLE scanner
│   │   ├── service/          # Services
│   │   └── ui/               # Jetpack Compose UI
│   └── test/                 # Unit tests (29)
├── build.gradle.kts
└── settings.gradle.kts

ios/                           # iOS app (SwiftUI)
├── EdgeClaw/
│   ├── EdgeClawApp.swift      # SwiftUI entry point
│   ├── Core/AppState.swift    # Global state
│   ├── Views/                 # 5 SwiftUI screens
│   ├── BLE/BLEScanner.swift   # CoreBluetooth
│   ├── Network/TCPClient.swift # NWConnection TCP
│   └── Generated/             # UniFFI bindings
├── build-rust.sh
├── generate-bindings.sh
└── IOS_QUICKSTART.md
```

## Build Commands

```bash
# Rust core
cd edgeclaw-core
cargo build --release        # Build
cargo test                   # Test (82)
cargo clippy --all-targets -- -D warnings  # Lint

# Kotlin app
cd ../android
./gradlew assembleDebug      # Debug APK
./gradlew test               # Test (29)
./gradlew lint               # Lint

# iOS (macOS VM)
cargo build --target x86_64-apple-ios --release
./ios/generate-bindings.sh   # UniFFI Swift bindings
```

## Architecture

```
Android App (Kotlin + Compose)
        ↓
    JNI Bridge
        ↓
Rust Core Library
├─ Identity (Ed25519/X25519)
├─ Session (AES-256-GCM)
├─ Policy (RBAC, 4 roles)
├─ Peer Manager
└─ ECNP Codec (Binary)
```

## Security Model

| Layer | Tech | Purpose |
|-------|------|---------|
| Signing | Ed25519 | Device auth |
| Exchange | X25519 ECDH | Key agreement |
| KDF | HKDF-SHA256 | Key derivation |
| Encrypt | AES-256-GCM | Confidentiality + integrity |
| Replay | Nonce + timestamp | Anti-replay |

## Key Modules

- `Identity` — Device fingerprinting & signing
- `SessionManager` — Encrypted channels
- `PolicyEngine` — 4-tier RBAC (Viewer/Operator/Admin/Owner)
- `FrameCodec` — ECNP v1.1 binary protocol
- `PeerManager` — Device discovery & connection tracking
- `BleScanner` — Bluetooth device discovery (Kotlin)

## Testing

All modules have unit tests in `#[cfg(test)]` blocks:

```bash
cargo test identity::tests
cargo test session::tests       # ECDH + AES-256-GCM
cargo test policy::tests        # RBAC
cargo test ecnp::tests          # Binary codec
```

## Security Invariants

✅ **NEVER violate**:
1. All connections authenticated (Ed25519)
2. All data encrypted (AES-256-GCM)
3. Nonce never reused
4. Key material zeroized
5. RBAC policy enforcement

## Coding Style

- Rust Edition 2021, MSRV 1.75
- No `unsafe` unless justified
- All public items have doc comments
- Conventional commits: `feat:`, `fix:`, `docs:`, `ci:`, `chore:`
- Kotlin: 1.9+, Jetpack Compose, Material 3

## Common Tasks

### Add a new message type

1. Define struct in `protocol.rs`
2. Implement `Frame` trait
3. Add tests with round-trip (encode/decode)
4. Update `README.md` message table

### Add a capability

1. Edit `ROLES` in `policy.rs`
2. Add tests for authorization
3. Update docs

### Update UI

1. Modify Kotlin Compose in `app/src/main/java/ui/`
2. Run `./gradlew lint`
3. Test on emulator

---

Always run `cargo test && cargo clippy && cargo fmt` before pushing!
