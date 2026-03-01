#!/bin/bash
# generate-bindings.sh — Generate Swift bindings from Rust using UniFFI
#
# Run this after installing uniffi-bindgen:
#   cargo install uniffi-bindgen
#
# The generated files go into ios/EdgeClaw/Generated/

set -e

RUST_DIR="$(dirname "$0")/../edgeclaw-core"
OUTPUT_DIR="$(dirname "$0")/EdgeClaw/Generated"

echo "🔗 Generating UniFFI Swift bindings..."

mkdir -p "$OUTPUT_DIR"

# Generate Swift bindings from UDL
uniffi-bindgen generate \
    "$RUST_DIR/src/edgeclaw.udl" \
    --language swift \
    --out-dir "$OUTPUT_DIR"

echo "✅ Swift bindings generated in $OUTPUT_DIR/"
echo "   Files:"
ls -la "$OUTPUT_DIR/"
