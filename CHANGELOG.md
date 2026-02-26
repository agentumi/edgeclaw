# Changelog

All notable changes to EdgeClaw Mobile will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- iOS app support (planned)
- Push notifications feature
- Advanced device grouping

### Changed
- UI improvements based on user feedback

---

## [0.1.0] - 2026-02-26

### Added
- Android app with Jetpack Compose Material 3 UI (5 screens)
- BLE device discovery and connection management
- Kotlin crypto engine (JCA AES-GCM)
- Kotlin RBAC policy engine
- EdgeClaw Core Rust library (47 tests)
  - Ed25519/X25519 identity management
  - ECDH + AES-256-GCM encrypted sessions
  - ECNP v1.1 binary codec
  - 4-tier RBAC policy engine (Viewer/Operator/Admin/Owner)
  - Peer management with discovery
- CI/CD pipeline (GitHub Actions)
  - Android: cross-compile (aarch64, armv7, x86_64), Gradle build, unit tests
  - Release: signed APK artifact on main branch

### Security
- Zero-trust security model: every connection authenticated + encrypted
- Ed25519 device identity with deterministic device IDs
- X25519 ECDH key exchange → HKDF-SHA256 → AES-256-GCM

---

[Unreleased]: https://github.com/agentumi/edgeclaw_mobile/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/agentumi/edgeclaw_mobile/releases/tag/v0.1.0
