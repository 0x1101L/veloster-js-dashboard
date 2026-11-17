#!/bin/sh
set -eu

TOOLS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$TOOLS_DIR/.." && pwd)
APP_PATH="$PROJECT_ROOT/dist/JS Dashboard Preview.app"

BUILD_DIR=$(mktemp -d /private/tmp/js-dashboard-preview-build.XXXXXX)
trap 'rm -rf "$BUILD_DIR"' EXIT
SDK_PATH=$(/usr/bin/xcrun --sdk macosx --show-sdk-path)
mkdir -p "$BUILD_DIR/module-cache"
for ARCH in arm64 x86_64; do
    /usr/bin/xcrun --sdk macosx swiftc -parse-as-library \
        -sdk "$SDK_PATH" -target "$ARCH-apple-macosx11.0" \
        -module-cache-path "$BUILD_DIR/module-cache" \
        "$TOOLS_DIR/macos_preview_app.swift" -o "$BUILD_DIR/preview-$ARCH"
done
/usr/bin/lipo -create "$BUILD_DIR/preview-arm64" "$BUILD_DIR/preview-x86_64" \
    -output "$BUILD_DIR/JS Dashboard Preview"
/usr/bin/lipo "$BUILD_DIR/JS Dashboard Preview" -verify_arch arm64 x86_64

mkdir -p "$APP_PATH/Contents/MacOS" "$APP_PATH/Contents/Resources"
cp "$TOOLS_DIR/macos_preview_app_Info.plist" "$APP_PATH/Contents/Info.plist"
mv "$BUILD_DIR/JS Dashboard Preview" "$APP_PATH/Contents/MacOS/JS Dashboard Preview"
/usr/bin/codesign --force --sign - "$APP_PATH"
/usr/bin/codesign --verify --strict --all-architectures "$APP_PATH"
printf 'Created: %s\n' "$APP_PATH"
/usr/bin/lipo -archs "$APP_PATH/Contents/MacOS/JS Dashboard Preview"
