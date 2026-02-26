# Contributing to EdgeClaw Mobile

Thank you for your interest in contributing! We welcome contributions from everyone.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Development Environment](#development-environment)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Commit Message Format](#commit-message-format)
- [Testing Requirements](#testing-requirements)
- [Security Considerations](#security-considerations)
- [License](#license)

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork:
   ```bash
   git clone https://github.com/<your-username>/edgeclaw_mobile.git
   cd edgeclaw_mobile
   ```
3. **Create a branch** from `dev`:
   ```bash
   git checkout -b feat/your-feature dev
   ```

## Development Environment

### Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Rust | 1.75+ | Core library ([rustup.rs](https://rustup.rs/)) |
| Android Studio | Hedgehog+ | Android IDE ([download](https://developer.android.com/studio)) |
| JDK | 17+ | Java runtime (bundled with Android Studio) |
| Android SDK | API 34 | Target platform |
| Android NDK | 27+ | Native code compilation |

### Setup

```bash
# Verify Rust installation
rustup show

# Install Android cross-compilation targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Install cargo-ndk for Android builds
cargo install cargo-ndk
```

## Development Workflow

### Build & Test

```bash
# Rust core — build, test, lint
cd edgeclaw-core
cargo build --release
cargo test                                    # 47 tests
cargo clippy --all-targets -- -D warnings     # Zero warnings
cargo fmt --check                             # Format check

# Android app — build, test, lint
cd ../android
./gradlew assembleDebug
./gradlew test                                # 29 tests
./gradlew lint
```

### Pre-Push Checklist

```bash
# Run everything before pushing
cd edgeclaw-core && cargo test && cargo clippy --all-targets -- -D warnings && cargo fmt
cd ../android && ./gradlew test && ./gradlew lint
```

## Coding Standards

### Rust

| Rule | Details |
|------|---------|
| Edition | 2021 |
| MSRV | 1.75+ |
| Errors | Use `thiserror` crate |
| Docs | All public APIs must have `///` doc comments |
| Tests | All modules must include `#[cfg(test)]` blocks |
| Warnings | Zero warnings policy (`-D warnings`) |
| Format | `cargo fmt` before every commit |
| Unsafe | Avoid `unsafe` unless absolutely justified |

### Kotlin

| Rule | Details |
|------|---------|
| Version | 1.9+ |
| UI | Jetpack Compose only (no XML layouts) |
| Design | Material 3 design system |
| Style | Follow [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html) |

## Pull Request Process

1. **One logical change per PR** — keep PRs focused and reviewable
2. **Tests required** — all new functionality must have tests
3. **Ensure CI passes**:
   - `cargo test` (47 tests)
   - `./gradlew test` (29 tests)
   - `cargo clippy --all-targets -- -D warnings`
   - `cargo fmt --check`
   - `./gradlew lint`
4. **Update documentation** — update CHANGELOG.md for user-facing changes
5. **Target `dev` branch** — PRs should target `dev`, not `main`
6. **Request review** — tag appropriate reviewers

## Commit Message Format

**Language Policy**: All commit messages must be written in **English only** to facilitate international collaboration and ensure consistency across the project.

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

| Type | Use For |
|------|---------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code restructure, no behavior change |
| `perf` | Performance improvement |
| `test` | Adding/updating tests |
| `ci` | CI/CD pipeline changes |
| `chore` | Build system, dependencies |

### Examples

```
feat(ble): add background scanning support

Implement foreground service for continuous BLE scanning
with configurable scan intervals and battery optimization.

Closes #23
```

```
fix(session): prevent nonce reuse in rapid reconnection

Add nonce deduplication cache with 30s TTL to handle
edge case of rapid disconnect/reconnect cycles.
```

## Testing Requirements

- **All new code** must have associated tests
- **Rust modules**: add tests in `#[cfg(test)]` blocks
- **Kotlin**: add unit tests in `app/src/test/`
- **Target**: maintain 100% pass rate across all 76 tests

## Security Considerations

When contributing security-related code:

- **Never** hardcode secrets or test keys
- **Always** zeroize key material after use
- **Never** reuse nonces
- **Always** validate RBAC before privileged operations
- Report vulnerabilities to [security@edgeclaw.dev](mailto:security@edgeclaw.dev)

See [SECURITY.md](SECURITY.md) for the full security policy.

## License

By contributing, you agree that your contributions are dual-licensed under:

- [MIT License](LICENSE-MIT)
- [Apache License 2.0](LICENSE-APACHE)

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community standards.

---

Thank you for helping build EdgeClaw!
