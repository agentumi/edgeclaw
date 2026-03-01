// !$*UTF8*$!
//
// NOTE: This is a PLACEHOLDER. The actual .xcodeproj must be created
// in Xcode on macOS. This file documents the required project settings.
//
// To create the project in Xcode:
//
// 1. Open Xcode → File → New → Project
// 2. iOS → App
// 3. Product Name: EdgeClaw
// 4. Organization: com.edgeclaw
// 5. Interface: SwiftUI
// 6. Language: Swift
// 7. Minimum Deployment: iOS 16.0
// 8. Save to: edgeclaw_mobile/ios/
//
// Then configure:
//
// Build Settings:
//   - PRODUCT_BUNDLE_IDENTIFIER = com.edgeclaw.mobile
//   - SWIFT_VERSION = 5.9
//   - IPHONEOS_DEPLOYMENT_TARGET = 16.0
//   - OTHER_LDFLAGS = -ledgeclaw_core
//   - LIBRARY_SEARCH_PATHS = $(PROJECT_DIR)/../edgeclaw-core/target/$(CONFIGURATION)/
//   - HEADER_SEARCH_PATHS = $(PROJECT_DIR)/EdgeClaw/Generated/
//
// Frameworks:
//   - CoreBluetooth.framework
//   - Network.framework
//
// Build Phases:
//   - Add "Run Script" phase before "Compile Sources":
//     Script: ${PROJECT_DIR}/build-rust.sh
//   - Link Binary With Libraries:
//     - libedgeclaw_core.a (from Rust build)
//
// Capabilities:
//   - Background Modes: Bluetooth Central, Bluetooth Peripheral
//
