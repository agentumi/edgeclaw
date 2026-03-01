# iOS Quick Start Guide

> EdgeClaw Mobile — iOS 앱 빌드 및 실행 가이드
> 대상: macOS VM (VMware) 또는 네이티브 Mac

---

## 사전 요구사항

| 항목 | 버전 | 설치 방법 |
|------|------|-----------|
| **macOS** | 14 (Sonoma)+ | VMware 또는 네이티브 Mac |
| **Xcode** | 15+ | App Store |
| **Rust** | 1.75+ | `rustup` |
| **iOS Target** | 16.0+ | Xcode에 포함 |
| **UniFFI** | 최신 | `cargo install uniffi-bindgen` |

---

## 1. macOS 환경 설정 (VMware에서)

### 1-1. Xcode 설치

```bash
# Command Line Tools (먼저)
xcode-select --install

# App Store에서 Xcode 15+ 설치 (약 12GB)
# 또는 developer.apple.com에서 .xip 다운로드

# iOS Simulator 런타임
xcodebuild -downloadPlatform iOS

# 라이선스 동의
sudo xcodebuild -license accept
```

### 1-2. Rust 설치

```bash
# Rust toolchain
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env

# iOS 크로스컴파일 타겟
rustup target add aarch64-apple-ios        # iPhone (ARM64)
rustup target add aarch64-apple-ios-sim    # Simulator (ARM64 Mac)
rustup target add x86_64-apple-ios         # Simulator (x86_64 VM)

# UniFFI bindgen
cargo install uniffi-bindgen

# cargo-lipo (유니버설 바이너리, 선택사항)
cargo install cargo-lipo
```

---

## 2. 프로젝트 가져오기

### 방법 A: VMware 공유 폴더

```bash
# VMware → VM → Settings → Options → Shared Folders
# D:\edgeclaw_mobile 공유 활성화

# macOS에서 접근:
cd /Volumes/VMware\ Shared\ Folders/edgeclaw_mobile
```

### 방법 B: Git clone

```bash
git clone <your-repo-url> ~/edgeclaw_mobile
cd ~/edgeclaw_mobile
```

### 방법 C: rsync/scp

```bash
# Windows IP가 192.168.x.x인 경우 (NAT 모드에서는 게이트웨이 사용)
scp -r user@host:/path/to/edgeclaw_mobile ~/edgeclaw_mobile
```

---

## 3. Rust Core 빌드

```bash
cd ~/edgeclaw_mobile/edgeclaw-core

# 기본 테스트 확인 (네이티브)
cargo test

# iOS Simulator용 빌드 (VMware x86_64)
cargo build --target x86_64-apple-ios --release

# 또는 ARM64 Simulator (Apple Silicon Mac)
cargo build --target aarch64-apple-ios-sim --release

# 실기기용 빌드 (ARM64)
cargo build --target aarch64-apple-ios --release
```

빌드 결과:
```
target/x86_64-apple-ios/release/libedgeclaw_core.a     # Simulator
target/aarch64-apple-ios/release/libedgeclaw_core.a     # Device
```

---

## 4. UniFFI Swift 바인딩 생성

```bash
cd ~/edgeclaw_mobile

# 바인딩 생성 스크립트 실행
chmod +x ios/generate-bindings.sh
./ios/generate-bindings.sh

# 또는 수동으로:
uniffi-bindgen generate \
    edgeclaw-core/src/edgeclaw.udl \
    --language swift \
    --out-dir ios/EdgeClaw/Generated/
```

생성되는 파일:
```
ios/EdgeClaw/Generated/
├── edgeclawFFI.h          # C 헤더
├── edgeclawFFI.modulemap  # 모듈 맵
└── edgeclaw.swift         # Swift 바인딩
```

---

## 5. Xcode 프로젝트 생성

### 5-1. 새 프로젝트

1. Xcode → **File → New → Project**
2. **iOS → App** 선택
3. 설정:
   - Product Name: `EdgeClaw`
   - Organization Identifier: `com.edgeclaw`
   - Interface: **SwiftUI**
   - Language: **Swift**
   - Minimum Deployment: **iOS 16.0**
4. 저장 위치: `~/edgeclaw_mobile/ios/`

### 5-2. Swift 소스 파일 추가

프로젝트 네비게이터에서 기존 파일 추가 (Drag & Drop 또는 File → Add Files):

```
EdgeClaw/
├── EdgeClawApp.swift       ← 앱 진입점 (기본 생성 파일 교체)
├── ContentView.swift       ← 탭 네비게이션
├── Core/
│   └── AppState.swift      ← 글로벌 상태
├── Views/
│   ├── DashboardView.swift
│   ├── PeersView.swift
│   ├── SessionsView.swift
│   ├── IdentityView.swift
│   └── SettingsView.swift
├── BLE/
│   └── BLEScanner.swift    ← CoreBluetooth
├── Network/
│   └── TCPClient.swift     ← NWConnection TCP
└── Generated/
    ├── edgeclawFFI.h
    ├── edgeclawFFI.modulemap
    └── edgeclaw.swift      ← UniFFI 바인딩
```

### 5-3. Build Settings 구성

프로젝트 → Build Settings:

| 설정 | 값 |
|------|-----|
| **Other Linker Flags** | `-ledgeclaw_core` |
| **Library Search Paths** | `$(PROJECT_DIR)/../edgeclaw-core/target/$(CURRENT_ARCH)-apple-ios/release` |
| **Header Search Paths** | `$(PROJECT_DIR)/EdgeClaw/Generated` |
| **Swift Version** | 5.9 |
| **iOS Deployment Target** | 16.0 |

### 5-4. Build Phases 설정

1. **Run Script** phase 추가 (Compile Sources 이전):
   ```bash
   "${PROJECT_DIR}/build-rust.sh"
   ```

2. **Link Binary With Libraries**:
   - `libedgeclaw_core.a` (수동 추가)
   - `CoreBluetooth.framework`
   - `Network.framework`
   - `Security.framework`

### 5-5. Capabilities

- **Background Modes**: Uses Bluetooth LE accessories (Central + Peripheral)

---

## 6. 빌드 & 실행

```bash
# Xcode에서:
# Product → Build (⌘B)
# Product → Run (⌘R) → iOS Simulator 선택

# 또는 커맨드라인:
cd ~/edgeclaw_mobile/ios
xcodebuild -project EdgeClaw.xcodeproj \
    -scheme EdgeClaw \
    -destination 'platform=iOS Simulator,name=iPhone 15' \
    build
```

---

## 7. 디렉토리 구조 (최종)

```
edgeclaw_mobile/
├── edgeclaw-core/                 # Rust core (공유)
│   ├── src/
│   │   ├── lib.rs
│   │   ├── identity.rs
│   │   ├── session.rs
│   │   ├── ecnp.rs
│   │   ├── protocol.rs
│   │   ├── policy.rs
│   │   ├── peer.rs
│   │   ├── sync.rs
│   │   ├── error.rs
│   │   ├── uniffi_bridge.rs       # UniFFI FFI 래퍼
│   │   └── edgeclaw.udl           # UniFFI 인터페이스 정의
│   └── Cargo.toml
├── android/                        # Android (Kotlin)
│   └── app/...
├── ios/                            # iOS (Swift)
│   ├── EdgeClaw/
│   │   ├── EdgeClawApp.swift
│   │   ├── ContentView.swift
│   │   ├── Info.plist
│   │   ├── Core/
│   │   │   └── AppState.swift
│   │   ├── Views/
│   │   │   ├── DashboardView.swift
│   │   │   ├── PeersView.swift
│   │   │   ├── SessionsView.swift
│   │   │   ├── IdentityView.swift
│   │   │   └── SettingsView.swift
│   │   ├── BLE/
│   │   │   └── BLEScanner.swift
│   │   ├── Network/
│   │   │   └── TCPClient.swift
│   │   └── Generated/             # UniFFI 자동 생성
│   ├── build-rust.sh
│   ├── generate-bindings.sh
│   └── XCODE_PROJECT_SETUP.md
└── README.md
```

---

## 8. 문제 해결

### Rust 빌드 오류

```bash
# 타겟 미설치
rustup target add x86_64-apple-ios

# 링커 오류
# → Xcode Command Line Tools가 설치되었는지 확인
xcode-select --print-path

# OpenSSL 관련 오류 (macOS)
brew install openssl
export OPENSSL_DIR=$(brew --prefix openssl)
```

### VMware 특이사항

```
⚠️ VMware macOS에서는 x86_64 아키텍처 사용
→ rustup target add x86_64-apple-ios  (Simulator용)
→ aarch64-apple-ios는 실기기 배포 시에만 필요

⚠️ BLE은 VMware에서 동작하지 않음
→ BLE 테스트는 실제 iPhone 또는 Mac 필요
→ UI/네트워크 테스트는 Simulator에서 가능
```

### IPA 사이즈 확인

```bash
# Archive 빌드
xcodebuild -project EdgeClaw.xcodeproj \
    -scheme EdgeClaw \
    -archivePath build/EdgeClaw.xcarchive \
    archive

# IPA 크기 확인
ls -lh build/EdgeClaw.xcarchive/Products/Applications/EdgeClaw.app
```

---

## 9. 개발 워크플로우

```
Windows (VS Code)           macOS VM (Xcode)
┌─────────────────┐        ┌──────────────────┐
│ Rust 코어 개발   │   →    │ cargo build iOS  │
│ cargo test      │ 공유   │ UniFFI 바인딩     │
│ cargo clippy    │ 폴더   │ SwiftUI 개발      │
└─────────────────┘        │ Simulator 실행    │
                           └──────────────────┘
```

1. **Windows**: Rust core 개발 + 테스트 (`cargo test`)
2. **macOS VM**: iOS 타겟 빌드 + Swift 바인딩 + Xcode 빌드
3. **Simulator**: UI + 네트워크 테스트 (BLE 제외)

---

> **참고**: Apple 라이선스 정책상 비-Apple 하드웨어에서의 macOS 가상화는
> 개인 학습/개발 목적으로만 사용하시기 바랍니다.

---

관련 문서:
- [XCODE_PROJECT_SETUP.md](XCODE_PROJECT_SETUP.md) — Xcode 프로젝트 설정 상세
- [edgeclaw.udl](../edgeclaw-core/src/edgeclaw.udl) — UniFFI 인터페이스 정의
- [README.md](../README.md) — 프로젝트 개요
