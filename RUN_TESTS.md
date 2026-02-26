# Running Tests

EdgeClaw Mobile has **76 tests** across Rust core and Android app.

---

## Quick Start

```bash
# Rust core (47 tests)
cd edgeclaw-core && cargo test

# Android app (29 tests)
cd android && ./gradlew test
```

## Rust Core — 47 Tests

### Run All

```bash
cd edgeclaw-core
cargo test
```

### Run by Module

| Module | Tests | Command |
|--------|-------|---------|
| Identity (Ed25519/X25519) | 4 | `cargo test identity::tests` |
| Session (ECDH + AES-GCM) | 5 | `cargo test session::tests` |
| Policy (RBAC) | 10 | `cargo test policy::tests` |
| Protocol (Messages) | 4 | `cargo test protocol::tests` |
| Peer (Discovery) | 6 | `cargo test peer::tests` |
| ECNP (Binary Codec) | 8 | `cargo test ecnp::tests` |
| Lib (Engine) | 9 | `cargo test lib::tests` |
| Error | 1 | `cargo test error::tests` |

### Options

```bash
# Verbose output
cargo test -- --nocapture

# Single-threaded (for debugging)
cargo test -- --test-threads=1

# Run specific test
cargo test identity::tests::test_keypair_generation
```

## Android App — 29 Tests

### Run All

```bash
cd android
./gradlew test
```

### Options

```bash
# Core tests only
./gradlew test --tests com.edgeclaw.mobile.core.*

# Verbose output
./gradlew test --info

# Specific test class
./gradlew test --tests com.edgeclaw.mobile.core.CryptoEngineTest
```

## Lint & Format

### Rust

```bash
# Clippy — zero warnings policy
cargo clippy --all-targets -- -D warnings

# Format check
cargo fmt --check

# Auto-format
cargo fmt
```

### Android

```bash
# Android lint
./gradlew lint
```

## Pre-Commit Checklist

Run all checks before committing:

```bash
cd edgeclaw-core && cargo test && cargo clippy --all-targets -- -D warnings && cargo fmt --check
cd ../android && ./gradlew test && ./gradlew lint
```

## Troubleshooting

### Test Timeouts

```bash
cargo test -- --test-threads=1 --nocapture
```

### Build Cache Issues

```bash
cargo clean && cargo test
```

### Android Emulator Issues

```bash
./gradlew connectedAndroidTest  # Requires running emulator
```

---

For more information, see [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md).
