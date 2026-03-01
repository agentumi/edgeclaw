# AGENTS.md

> Instructions for AI coding agents working on EdgeClaw Mobile.

## Quick Reference

| Item | Details |
|------|---------|
| **Rust MSRV** | 1.75+ |
| **Kotlin** | 1.9+ |
| **Swift** | 5.9+ |
| **Android API** | 34+ |
| **iOS** | 16.0+ |
| **Tests** | 82 Rust + 29 Kotlin |
| **Protocol** | ECNP v1.1 binary |
| **Security** | Zero-trust: Ed25519 + X25519 + AES-256-GCM |

## Build & Test

```bash
# Rust core
cd edgeclaw-core && cargo build --release && cargo test

# Kotlin app
cd ../android && ./gradlew assembleDebug && ./gradlew test

# Lint all
cargo clippy --all-targets -- -D warnings
cargo fmt --check
./gradlew lint
```

## Code Conventions

### Rust
- `Edition 2021`, MSRV `1.75+`
- Use `thiserror` for errors
- All public APIs must have doc comments (`///`)
- All modules must include unit tests in `#[cfg(test)]` blocks
- Zero warnings: `cargo clippy --all-targets -- -D warnings`
- Format: `cargo fmt` before commit

### Kotlin
- `Kotlin 1.9+`
- Jetpack Compose for UI (no XML)
- Material 3 design system
- Follow [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html)

## Architecture Principles

1. **Zero Trust** — Every connection authenticated + encrypted
2. **Minimal Footprint** — Target <5MB RAM, <50MB APK
3. **No Unsafe** — Avoid `unsafe` Rust blocks
4. **Test Coverage** — All new code must have tests
5. **Binary Protocol** — Use ECNP, not JSON for device communication

## Security Requirements

- **Key Material**: MUST be zeroized after use
- **Session Keys**: ECDH → HKDF-SHA256 → AES-256-GCM
- **Nonce Reuse**: NEVER acceptable
- **Policy Checks**: All privileged operations MUST pass RBAC
- **Audit Logs**: MUST be hash-chained (SHA-256)

## File Organization

```
edgeclaw_mobile/
├── edgeclaw-core/
│   ├── src/
│   │   ├── lib.rs            # Public API
│   │   ├── identity.rs       # Ed25519/X25519
│   │   ├── session.rs        # AES-256-GCM
│   │   ├── protocol.rs       # Message types
│   │   ├── policy.rs         # RBAC (4 roles)
│   │   ├── peer.rs           # Discovery
│   │   ├── ecnp.rs           # Binary codec
│   │   ├── sync.rs           # Desktop sync
│   │   ├── uniffi_bridge.rs  # UniFFI FFI bridge
│   │   ├── edgeclaw.udl      # UniFFI interface
│   │   └── error.rs          # Errors
│   └── Cargo.toml
├── android/
│   ├── app/src/
│   │   ├── main/
│   │   │   ├── java/com/edgeclaw/mobile/
│   │   │   │   ├── core/     # Engine, crypto
│   │   │   │   ├── ble/      # BLE scanner
│   │   │   │   └── ui/       # Compose UI
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── ios/
│   ├── EdgeClaw/
│   │   ├── EdgeClawApp.swift # SwiftUI entry
│   │   ├── Core/             # AppState
│   │   ├── Views/            # 5 SwiftUI screens
│   │   ├── BLE/              # CoreBluetooth
│   │   ├── Network/          # NWConnection TCP
│   │   └── Generated/        # UniFFI bindings
│   ├── build-rust.sh
│   ├── generate-bindings.sh
│   └── IOS_QUICKSTART.md
└── README.md
```

## PR Checklist

- [ ] One logical change per PR
- [ ] Tests added for new functionality
- [ ] `cargo test` passes (82 tests)
- [ ] `./gradlew test` passes (29 tests)
- [ ] `cargo clippy --all-targets -- -D warnings` (zero warnings)
- [ ] `cargo fmt` run
- [ ] `./gradlew lint` passes
- [ ] CHANGELOG.md updated
- [ ] CLA signed (commit metadata serves as signature)

## Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**: `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `perf:`, `test:`, `ci:`, `chore:`

**Example**:
```
feat(protocol): add heartbeat message support

Implement ECNP v1.1 Heartbeat (0x04) message type with
uptime tracking and session info.

Closes #42
```

## Testing Modules

```bash
# Rust (47 tests total)
cargo test identity::tests      # Ed25519/X25519 (4 tests)
cargo test session::tests       # ECDH + AES-GCM (5 tests)
cargo test policy::tests        # RBAC (10 tests)
cargo test protocol::tests      # Messages (4 tests)
cargo test peer::tests          # Discovery (6 tests)
cargo test ecnp::tests          # Binary codec (8 tests)
cargo test lib::tests           # Library (9 tests)

# Kotlin (29 tests)
./gradlew test --tests com.edgeclaw.mobile.core.*
```

## Security Checklist

- [ ] All encryption uses `aes-gcm` crate
- [ ] All signatures use `ed25519-dalek`
- [ ] All ECDH uses `x25519-dalek`
- [ ] Nonces are cryptographically random (12 bytes)
- [ ] Key material zeroized after use
- [ ] No hardcoded secrets or test keys in production
- [ ] RBAC checks before privileged operations

---

**Questions?** See [README.md](README.md), [CONTRIBUTING.md](CONTRIBUTING.md), or [CLAUDE.md](CLAUDE.md).
