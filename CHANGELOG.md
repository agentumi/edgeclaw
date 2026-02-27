# Changelog

All notable changes to EdgeClaw Mobile will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- iOS app support
- Push notifications (FCM)
- Advanced device grouping

---

## [1.0.0] - 2026-02-27

### Added
- Chat screen with AI-powered command interface
- Voice input support (Android SpeechRecognizer, Korean primary)
- Quick action buttons with Korean labels for elderly-friendly UX
- ChatEngine singleton with local command processing
- Chat data models (ChatMessageModel, QuickAction, ParsedIntent)
- Korean/English bilingual command parsing
- Loading indicators and message state management
- ExtendedFloatingActionButton for chat entry on Dashboard
- Chat route in navigation graph
- 29 Kotlin tests passing, 0 warnings

### Changed
- Dashboard now features Chat FAB as primary action
- Discovery button moved to secondary SmallFAB
- Fixed deprecated icon warnings (AutoMirrored icons)

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
