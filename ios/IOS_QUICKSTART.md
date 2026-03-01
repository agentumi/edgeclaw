# iOS Quick Start Guide

> EdgeClaw Mobile V3.0 — iOS 앱 빌드 및 실행 가이드
> 대상: macOS VM (VMware) 또는 네이티브 Mac
> 업데이트: 2026-03-01

---

## 사전 요구사항

| 항목 | 버전 | 설치 방법 |
|------|------|-----------|
| **macOS** | 14 (Sonoma)+ | VMware 또는 네이티브 Mac |
| **Xcode** | 15+ | App Store |
| **Rust** | 1.75+ | `rustup` |
| **iOS Target** | 17.0+ | Xcode에 포함 |
| **UniFFI** | 최신 | `cargo install uniffi-bindgen` |
| **Fastlane** | 최신 | `brew install fastlane` (선택) |

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
├── EdgeClawApp.swift       ← 앱 진입점 + AppDelegate (APNs + BGTask)
├── ContentView.swift       ← 6탭 네비게이션
├── Info.plist              ← 권한 + Background Modes
├── Core/
│   ├── AppState.swift      ← 글로벌 상태
│   ├── Models.swift        ← 데이터 모델 + DataStore (V3.0)
│   ├── KeyChainManager.swift ← Keychain 래퍼 (V3.0)
│   └── BiometricAuthGate.swift ← Face ID/Touch ID 게이트 (V3.0)
├── Views/
│   ├── DashboardView.swift
│   ├── ChatView.swift      ← 에이전트 채팅 UI (V3.0)
│   ├── PeersView.swift     ← 피어 + 그룹 관리 CRUD
│   ├── SessionsView.swift
│   ├── IdentityView.swift
│   └── SettingsView.swift  ← 생체인증 토글 포함
├── BLE/
│   └── BLEScanner.swift    ← CoreBluetooth
├── Network/
│   ├── TCPClient.swift     ← NWConnection TCP
│   └── SyncService.swift   ← 백그라운드 동기화 (V3.0)
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
   - **iOS Deployment Target** | 17.0 |

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
   - `LocalAuthentication.framework` (Face ID/Touch ID)

### 5-5. Capabilities

- **Background Modes**: 
  - Uses Bluetooth LE accessories (Central + Peripheral)
  - Background fetch
  - Background processing
  - Remote notifications
- **Push Notifications**: APNs 설정

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
│   │   ├── EdgeClawApp.swift      # 앱 진입점 + AppDelegate
│   │   ├── ContentView.swift      # 6탭 네비게이션
│   │   ├── Info.plist             # 권한 + Background Modes
│   │   ├── Core/
│   │   │   ├── AppState.swift     # 글로벌 상태
│   │   │   ├── Models.swift       # 데이터 모델 + DataStore
│   │   │   ├── KeyChainManager.swift # Keychain 래퍼
│   │   │   └── BiometricAuthGate.swift # Face ID 게이트
│   │   ├── Views/
│   │   │   ├── DashboardView.swift
│   │   │   ├── ChatView.swift     # 에이전트 채팅
│   │   │   ├── PeersView.swift    # 피어 + 그룹 관리
│   │   │   ├── SessionsView.swift
│   │   │   ├── IdentityView.swift
│   │   │   └── SettingsView.swift # 생체인증 설정
│   │   ├── BLE/
│   │   │   └── BLEScanner.swift   # CoreBluetooth
│   │   ├── Network/
│   │   │   ├── TCPClient.swift    # NWConnection TCP
│   │   │   └── SyncService.swift  # 백그라운드 동기화
│   │   └── Generated/             # UniFFI 자동 생성
│   ├── EdgeClawTests/
│   │   └── EdgeClawTests.swift    # 35개 단위 테스트
│   ├── fastlane/
│   │   ├── Fastfile               # TestFlight/App Store 배포
│   │   └── Appfile                # 앱 식별자 설정
│   ├── build-rust.sh
│   ├── generate-bindings.sh
│   └── IOS_QUICKSTART.md          # 이 문서
└── README.md
```

---

## 7.1. V3.0 신규 기능

### 생체 인증 (Face ID / Touch ID)

앱 시작 시 생체 인증 요구 (선택):

1. **Settings** 탭 → **생체 인증** 토글 ON
2. `BiometricAuthGate`가 앱 실행 시 자동으로 Face ID/Touch ID 요구
3. 실패 시 패스코드 폴백 지원

**Info.plist** 필수 키:
```xml
<key>NSFaceIDUsageDescription</key>
<string>EdgeClaw uses Face ID to protect your device identity and sessions.</string>
```

### 에이전트 채팅 (ChatView)

6번째 탭에서 데스크탑 에이전트와 실시간 명령 수행:

- **에이전트 선택**: default / system / security / devops
- **퀵 액션**: Status, Peers, System Info, Health Check 등 8가지
- **코드 블록**: 실행 결과를 monospace 폰트로 표시 + 복사 지원

### 백그라운드 동기화 (SyncService)

데스크탑 에이전트와 7가지 항목 자동 동기화:

| 동기화 항목 | 설명 |
|-------------|------|
| `config` | 설정 동기화 |
| `identity` | 디바이스 ID 동기화 |
| `peerList` | 피어 목록 |
| `sessionState` | 세션 상태 |
| `policyUpdate` | RBAC 정책 |
| `activityLog` | 팀 활동 로그 |
| `statusPush` | 상태 푸시 |

**BGTaskScheduler** 등록 (iOS 13+):
```swift
BGTaskScheduler.shared.register(
    forTaskWithIdentifier: "com.edgeclaw.mobile.sync",
    using: nil
) { task in ... }
```

### KeyChain 보안 저장

`KeyChainManager`로 Ed25519/X25519 키 자료를 iOS Keychain에 안전하게 저장:

```swift
// 키 저장
try KeyChainManager.storeEd25519PrivateKey(keyData)

// 키 로드
let key = try KeyChainManager.loadEd25519PrivateKey()
```

접근 제어: `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (디바이스 잠금 시 접근 불가)

### 피어 그룹 관리

PeersView에서 피어를 그룹으로 관리:

1. **그룹 생성**: 이름, 아이콘, 색상 지정
2. **피어 할당**: Context Menu → "Add to Group"
3. **그룹 필터**: 상단 가로 스크롤 바에서 그룹별 필터링
4. **CRUD**: 생성/수정/삭제/정렬 (GroupManagementView)

### APNs 푸시 알림

`AppDelegate`에서 Apple Push Notification 자동 등록:

```swift
// 디바이스 토큰 → UserDefaults 저장
UserDefaults.standard.set(tokenString, forKey: "edgeclaw.apnsToken")
```

### Fastlane 배포

```bash
# 테스트
fastlane test

# TestFlight 배포
fastlane beta

# App Store 배포
fastlane release
```

---

## 8. BLE 연결 가이드

### 8-1. EdgeClaw BLE 서비스

| 항목 | 값 |
|------|-----|
| **Service UUID** | `EC1A0001-EDGE-CLAW-BLE0-SERVICE00001` |
| **프로토콜** | ECNP v1.1 binary framing |
| **인증** | Ed25519 + X25519 ECDH |
| **암호화** | AES-256-GCM |

### 8-2. BLE 스캔 시작

1. **Peers** 탭 → 우상단 안테나 아이콘 탭
2. `BLEScanner`가 CoreBluetooth 스캔 시작
3. EdgeClaw 서비스 UUID를 광고하는 디바이스 자동 필터링
4. 발견된 피어가 목록에 추가 (RSSI + 디바이스 타입 표시)

### 8-3. 연결 흐름

```
[iOS App]  ─── BLE Scan ──→  [Desktop Agent]
    │                              │
    ├── ECDH Key Exchange ────────→├
    │                              │
    ├── Ed25519 Auth (mutual) ────→├
    │                              │
    └── AES-256-GCM Session ──────→└── ECNP v1.1 frames
```

### 8-4. 제한 사항

- VMware에서 BLE 동작 불가 → 실기기 또는 네이티브 Mac 필요
- iOS Simulator는 CoreBluetooth 미지원
- TCP 연결은 Simulator에서도 동작 (TCPClient)

---

## 9. 문제 해결

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
- [edgeclaw.udl](../edgeclaw-core/src/edgeclaw.udl) — UniFFI 인터페이스 정의
- [README.md](../README.md) — 프로젝트 개요
- [AGENTS.md](../AGENTS.md) — AI 에이전트 개발 가이드
- [CHANGELOG.md](../CHANGELOG.md) — 변경 이력
