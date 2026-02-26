# EdgeClaw Mobile — APK 빌드 & 배포 가이드

**문서 버전:** 1.0  
**작성일:** 2026-02-26  
**대상:** 개발자, DevOps

---

## 📋 목차

1. [사전 준비](#1-사전-준비)
2. [Debug APK 빌드](#2-debug-apk-빌드)
3. [Release APK 빌드](#3-release-apk-빌드)
4. [앱 서명 설정](#4-앱-서명-설정)
5. [배포 방법](#5-배포-방법)
6. [CI/CD 자동화](#6-cicd-자동화)

---

## 1. 사전 준비

### 1.1 필수 도구 설치

```bash
# 1. Rust (Rust core 빌드용)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi

# 2. Android SDK (Android Studio 설치 권장)
# https://developer.android.com/studio 에서 다운로드

# 3. Android NDK
# Android Studio → SDK Manager → SDK Tools → NDK 체크

# 4. Java 17+
java -version  # 17 이상 확인
```

### 1.2 환경 변수 설정

**Windows (PowerShell):**
```powershell
$env:ANDROID_HOME = "C:\Users\사용자명\AppData\Local\Android\Sdk"
$env:ANDROID_NDK_HOME = "$env:ANDROID_HOME\ndk\26.3.11579264"
```

**Linux/Mac:**
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### 1.3 Rust Core 빌드

```bash
cd edgeclaw_mobile/edgeclaw-core

# Android 타겟 빌드
cargo build --target aarch64-linux-android --release
cargo build --target armv7-linux-androideabi --release

# 빌드된 .so 파일을 android/app/src/main/jniLibs에 복사
```

---

## 2. Debug APK 빌드

### 2.1 Gradle로 빌드

```bash
cd edgeclaw_mobile/android

# Windows
.\gradlew assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### 2.2 출력 파일

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### 2.3 설치 & 테스트

```bash
# USB 디버깅 활성화된 기기에 설치
adb install app-debug.apk

# 또는 Logcat으로 로그 확인
adb logcat | grep EdgeClaw
```

---

## 3. Release APK 빌드

### 3.1 서명 키 생성

```bash
# 키스토어 생성 (최초 1회)
keytool -genkey -v -keystore edgeclaw-release.keystore \
  -alias edgeclaw \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# 입력 정보:
# - 비밀번호: ******** (안전하게 보관)
# - 이름: EdgeClaw Team
# - 조직: EdgeClaw
# - 위치: Seoul
# - 국가: KR
```

**⚠️ 중요: 키스토어 파일과 비밀번호를 안전하게 백업하세요!**

### 3.2 Gradle 서명 설정

`android/keystore.properties` 파일 생성:

```properties
storeFile=../edgeclaw-release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=edgeclaw
keyPassword=YOUR_KEY_PASSWORD
```

**⚠️ `keystore.properties`를 `.gitignore`에 추가하세요!**

### 3.3 `build.gradle.kts` 수정

`android/app/build.gradle.kts`에 다음 추가:

```kotlin
// 파일 상단에 추가
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    // ... 기존 설정 ...

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 3.4 Release APK 빌드

```bash
cd android

# Release APK 빌드
./gradlew assembleRelease

# 혹은 App Bundle (Google Play 권장)
./gradlew bundleRelease
```

### 3.5 출력 파일

```
# APK
android/app/build/outputs/apk/release/app-release.apk

# AAB (App Bundle)
android/app/build/outputs/bundle/release/app-release.aab
```

---

## 4. 앱 서명 설정

### 4.1 서명 확인

```bash
# APK 서명 확인
jarsigner -verify -verbose -certs app-release.apk

# SHA-256 fingerprint 확인 (Google Play, Firebase 등록용)
keytool -list -v -keystore edgeclaw-release.keystore -alias edgeclaw
```

### 4.2 Play App Signing (권장)

Google Play에 업로드 시 Google이 자동으로 추가 서명을 관리합니다.

1. Google Play Console → 앱 → Release → App integrity
2. App signing key 확인
3. Upload key certificate (SHA-256) 등록

---

## 5. 배포 방법

### 5.1 GitHub Releases (오픈소스)

```bash
# 1. GitHub Release 생성
gh release create v1.0.0 \
  android/app/build/outputs/apk/release/app-release.apk \
  --title "EdgeClaw Mobile v1.0.0" \
  --notes "첫 번째 공개 릴리스"

# 2. 사용자는 GitHub에서 APK 직접 다운로드
```

**장점:**
- 무료
- 빠른 배포
- 버전 관리 용이

**단점:**
- "알 수 없는 출처" 허용 필요
- 자동 업데이트 불가

### 5.2 Google Play Store (공식)

#### 5.2.1 Console 설정

1. [Google Play Console](https://play.google.com/console) 가입
2. 새 앱 만들기
   - 앱 이름: EdgeClaw Mobile
   - 기본 언어: 한국어
   - 앱 유형: 앱
   - 무료/유료: 무료

#### 5.2.2 스토어 등록 정보 작성

- **짧은 설명** (80자):
  ```
  Edge 장비를 안전하게 관리하는 분산형 모바일 앱
  ```

- **자세한 설명** (4000자):
  ```
  EdgeClaw Mobile은 스마트폰으로 서버, IoT 기기, PC 등을 
  안전하게 모니터링하고 제어할 수 있는 분산형 엣지 컴퓨팅 앱입니다.
  
  주요 기능:
  • BLE/WiFi로 장비 자동 발견
  • 실시간 상태 모니터링 (CPU, 메모리, 디스크)
  • 파일 전송 (암호화)
  • 원격 명령 실행
  • 다중 장비 관리
  
  보안:
  • Ed25519 장비 인증
  • AES-256-GCM 종단간 암호화
  • RBAC 권한 관리
  
  프라이버시:
  • 데이터는 기기 간 직접 전송 (P2P)
  • 중앙 서버 불필요
  • 오픈소스 (Apache 2.0 / MIT)
  ```

- **스크린샷**: 5~8장 (1080x1920)
- **아이콘**: 512x512 PNG

#### 5.2.3 콘텐츠 등급

1. 설문 작성 (폭력성, 성적 콘텐츠 등)
2. EdgeClaw는 일반적으로 **모든 연령** 등급

#### 5.2.4 가격 및 배포

- 국가: 전체 또는 선택 (한국, 미국 등)
- 가격: 무료

#### 5.2.5 앱 업로드

```bash
# AAB 업로드 (권장)
./gradlew bundleRelease

# Google Play Console → 프로덕션 → 새 버전 만들기
# app-release.aab 업로드
```

#### 5.2.6 심사 제출

- 내부 테스트 → 비공개 테스트 → 공개 테스트 → 프로덕션 순서 권장
- 심사 기간: 평균 1~3일

### 5.3 Firebase App Distribution (베타 테스트)

```bash
# 1. Firebase CLI 설치
npm install -g firebase-tools
firebase login

# 2. Firebase 프로젝트 연동
firebase init

# 3. APK 배포
firebase appdistribution:distribute \
  android/app/build/outputs/apk/release/app-release.apk \
  --app 1:123456789:android:abc123 \
  --groups "testers" \
  --release-notes "v1.0.0 베타 테스트"
```

**장점:**
- 빠른 베타 배포
- 테스터 그룹 관리
- 크래시 리포트 통합

### 5.4 F-Droid (오픈소스 스토어)

1. [F-Droid 등록 요청](https://gitlab.com/fdroid/rfp/-/issues)
2. 리포지토리 메타데이터 작성
3. 빌드 재현성 보장 (deterministic build)

---

## 6. CI/CD 자동화

### 6.1 GitHub Actions 워크플로우

`.github/workflows/android-release.yml`:

```yaml
name: Android Release Build

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Set up Rust
        uses: dtolnay/rust-toolchain@stable
        with:
          targets: aarch64-linux-android
      
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
      
      - name: Build Rust Core
        run: |
          cd edgeclaw-core
          cargo build --target aarch64-linux-android --release
      
      - name: Decode Keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo $KEYSTORE_BASE64 | base64 -d > android/edgeclaw-release.keystore
      
      - name: Build Release APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          cd android
          ./gradlew assembleRelease \
            -Pandroid.injected.signing.store.file=../edgeclaw-release.keystore \
            -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD \
            -Pandroid.injected.signing.key.alias=edgeclaw \
            -Pandroid.injected.signing.key.password=$KEY_PASSWORD
      
      - name: Upload to GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: android/app/build/outputs/apk/release/app-release.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 6.2 Secrets 설정

GitHub Repository → Settings → Secrets and variables → Actions:

```
KEYSTORE_BASE64: (keystore 파일을 base64 인코딩한 값)
KEYSTORE_PASSWORD: ********
KEY_PASSWORD: ********
```

**Base64 인코딩 방법:**
```bash
# Windows
certutil -encode edgeclaw-release.keystore keystore.txt
# keystore.txt 내용 복사 (BEGIN/END 줄 제외)

# Linux/Mac
base64 edgeclaw-release.keystore | pbcopy
```

### 6.3 자동 배포 트리거

```bash
# Git 태그 생성 → 자동 빌드 → GitHub Release 생성
git tag v1.0.0
git push origin v1.0.0

# Actions 탭에서 진행 상황 확인
```

---

## 7. 배포 체크리스트

### 출시 전 점검

- [ ] Rust core 테스트 통과 (`cargo test`)
- [ ] Android 테스트 통과 (`./gradlew test`)
- [ ] ProGuard 설정 확인
- [ ] 서명 키 백업 완료
- [ ] 버전 코드/이름 업데이트
- [ ] CHANGELOG.md 업데이트
- [ ] 스크린샷 최신화
- [ ] 개인정보처리방침 링크 확인
- [ ] 라이선스 명시 (Apache 2.0 / MIT)

### Play Store 출시 전

- [ ] 앱 아이콘 512x512 준비
- [ ] 스크린샷 5~8장 준비
- [ ] 기능 그래픽 1024x500
- [ ] 짧은/자세한 설명 작성
- [ ] 콘텐츠 등급 완료
- [ ] 개인정보처리방침 URL
- [ ] 지원 이메일 설정

---

## 8. 문제 해결

### 8.1 "알 수 없는 출처" 오류

GitHub Release APK 설치 시:

**안드로이드 설정 → 보안 → 알 수 없는 출처 허용**

### 8.2 서명 오류

```
INSTALL_PARSE_FAILED_NO_CERTIFICATES
```

해결: `keystore.properties` 경로 및 비밀번호 확인

### 8.3 R8/ProGuard 오류

`-keep` 규칙을 `proguard-rules.pro`에 추가:

```proguard
-keep class com.edgeclaw.mobile.** { *; }
-keepclassmembers class * {
    native <methods>;
}
```

### 8.4 Java/JDK 설치 문제

**증상:**
```
'java' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 없는 프로그램 이름으로 인식되지 않습니다
'keytool' 용어가 인식되지 않습니다
```

**해결:**
1. OpenJDK 17 설치:
```bash
winget install Microsoft.OpenJDK.17 --source winget
```

2. 새 터미널을 열어 환경 변수 새로고침:
```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
java -version
```

### 8.5 Gradle Wrapper JAR 누락

**증상:**
```
오류: 기본 클래스 org.gradle.wrapper.GradleWrapperMain을(를) 찾거나 로드할 수 없습니다.
원인: java.lang.ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain
```

**해결:**
```bash
cd android
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle\wrapper\gradle-wrapper.jar"
```

### 8.6 settings.gradle.kts 컴파일 오류

**증상:**
```
Unresolved reference: dependencyResolution
```

**해결:**
`settings.gradle.kts`에서 `dependencyResolution`을 `dependencyResolutionManagement`로 변경:

```kotlin
// 잘못된 코드
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

// 올바른 코드
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

### 8.7 build.gradle.kts Import 오류

**증상:**
```
Unresolved reference: util
Unresolved reference: io
```

**해결:**
파일 상단에 import 추가:

```kotlin
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    // ...
}
```

### 8.8 Android SDK 경로 문제

**증상:**
```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file
```

**해결 방법 1: Android Studio 설치 (권장)**
```bash
winget install Google.AndroidStudio --source winget
```

**해결 방법 2: local.properties 파일 생성**
```properties
# android/local.properties
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk\\ndk\\26.3.11579264
```

**해결 방법 3: 환경 변수 설정**
```powershell
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\사용자명\AppData\Local\Android\Sdk", "User")
```

---

## 9. 빌드 환경 설정 요약

### 필수 요구사항

| 도구 | 버전 | 설치 방법 |
|------|------|-----------|
| Java JDK | 17+ | `winget install Microsoft.OpenJDK.17 --source winget` |
| Android Studio | 최신 | `winget install Google.AndroidStudio --source winget` |
| Gradle Wrapper | 8.7+ | 프로젝트에 포함 (gradle-wrapper.jar) |

### 빌드 전 체크리스트

- [ ] Java 17+ 설치 확인 (`java -version`)
- [ ] Android SDK 설치 확인 (Android Studio 또는 CLI tools)
- [ ] ANDROID_HOME 환경 변수 설정
- [ ] gradle-wrapper.jar 파일 존재 확인
- [ ] keystore.properties 파일 생성 (Release 빌드 시)
- [ ] settings.gradle.kts 정상 동작 확인
- [ ] build.gradle.kts import 문 확인

---

## 10. 참고 자료

- [Android Developer Docs](https://developer.android.com/studio/publish)
- [Google Play Console](https://play.google.com/console)
- [F-Droid Submission](https://f-droid.org/docs/Submitting_to_F-Droid/)
- [Firebase App Distribution](https://firebase.google.com/docs/app-distribution)

---

**문의:** GitHub Issues 또는 softkids1@naver.com
