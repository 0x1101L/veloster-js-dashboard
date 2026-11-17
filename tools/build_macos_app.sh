#!/bin/sh
set -eu

TOOLS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$TOOLS_DIR/.." && pwd)
APP_PATH="$PROJECT_ROOT/dist/JS Dashboard Preview.app"

mkdir -p "$APP_PATH/Contents/MacOS" "$APP_PATH/Contents/Resources"
cp "$TOOLS_DIR/macos_preview_app_Info.plist" "$APP_PATH/Contents/Info.plist"
BUILD_DIR=$(mktemp -d /private/tmp/js-dashboard-preview-build.XXXXXX)
trap 'rm -rf "$BUILD_DIR"' EXIT
/usr/bin/swiftc -parse-as-library "$TOOLS_DIR/macos_preview_app.swift" -o "$BUILD_DIR/JS Dashboard Preview"
mv "$BUILD_DIR/JS Dashboard Preview" "$APP_PATH/Contents/MacOS/JS Dashboard Preview"
printf 'Created: %s\n' "$APP_PATH"
