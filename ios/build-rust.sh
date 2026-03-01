#!/bin/bash
# build-rust.sh — Build edgeclaw-core for iOS targets
#
# Called from Xcode "Run Script" build phase.
# Produces a universal .a static library for iOS.

set -e

RUST_DIR="$(dirname "$0")/../edgeclaw-core"
cd "$RUST_DIR"

echo "🦀 Building edgeclaw-core for iOS..."

# Detect Xcode SDK & architecture
if [ "$PLATFORM_NAME" = "iphonesimulator" ]; then
    if [ "$(uname -m)" = "arm64" ]; then
        RUST_TARGET="aarch64-apple-ios-sim"
    else
        RUST_TARGET="x86_64-apple-ios"
    fi
else
    RUST_TARGET="aarch64-apple-ios"
fi

PROFILE="${CONFIGURATION:-Release}"
if [ "$PROFILE" = "Debug" ]; then
    CARGO_FLAGS=""
    TARGET_SUBDIR="debug"
else
    CARGO_FLAGS="--release"
    TARGET_SUBDIR="release"
fi

echo "  Target: $RUST_TARGET"
echo "  Profile: $PROFILE"

# Ensure target is installed
rustup target add "$RUST_TARGET" 2>/dev/null || true

# Build
cargo build --target "$RUST_TARGET" $CARGO_FLAGS

# Copy the static library to where Xcode expects it
BUILT_LIB="target/$RUST_TARGET/$TARGET_SUBDIR/libedgeclaw_core.a"
OUTPUT_DIR="$BUILT_PRODUCTS_DIR"

if [ -n "$OUTPUT_DIR" ] && [ -f "$BUILT_LIB" ]; then
    cp "$BUILT_LIB" "$OUTPUT_DIR/"
    echo "  Copied $BUILT_LIB → $OUTPUT_DIR/"
fi

echo "✅ edgeclaw-core built for $RUST_TARGET"
