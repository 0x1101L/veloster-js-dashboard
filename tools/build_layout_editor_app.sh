#!/bin/sh
set -eu

TOOLS_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$TOOLS_DIR/.." && pwd)
APP_PATH="$PROJECT_ROOT/dist/JS Dashboard Layout Editor.app"
BUILD_DIR=$(mktemp -d /private/tmp/js-dashboard-layout-editor-build.XXXXXX)
trap 'rm -rf "$BUILD_DIR"' EXIT
REPLACE=0
if [ "${1:-}" = "--replace" ]; then
    REPLACE=1
elif [ "$#" -ne 0 ]; then
    echo "Usage: $0 [--replace]" >&2
    exit 2
fi

if [ -e "$APP_PATH" ] && [ "$REPLACE" -ne 1 ]; then
    echo "Refusing to replace existing app: $APP_PATH" >&2
    exit 1
fi
STAGED_APP="$BUILD_DIR/JS Dashboard Layout Editor.app"

SDK_PATH=$(/usr/bin/xcrun --sdk macosx --show-sdk-path)
mkdir -p "$BUILD_DIR/module-cache"
for ARCH in arm64 x86_64; do
    /usr/bin/xcrun --sdk macosx swiftc -parse-as-library \
        -sdk "$SDK_PATH" -target "$ARCH-apple-macosx11.0" \
        -module-cache-path "$BUILD_DIR/module-cache" \
        "$TOOLS_DIR/layout_editor_app.swift" -o "$BUILD_DIR/editor-$ARCH"
done
/usr/bin/lipo -create "$BUILD_DIR/editor-arm64" "$BUILD_DIR/editor-x86_64" \
    -output "$BUILD_DIR/Dashboard Layout Editor"
/usr/bin/lipo "$BUILD_DIR/Dashboard Layout Editor" -verify_arch arm64 x86_64

mkdir -p "$STAGED_APP/Contents/MacOS" "$STAGED_APP/Contents/Resources"
cp "$TOOLS_DIR/layout_editor_app_Info.plist" "$STAGED_APP/Contents/Info.plist"
mv "$BUILD_DIR/Dashboard Layout Editor" "$STAGED_APP/Contents/MacOS/Dashboard Layout Editor"
/usr/bin/codesign --force --sign - "$STAGED_APP"
/usr/bin/codesign --verify --strict --all-architectures "$STAGED_APP"
if [ -e "$APP_PATH" ]; then
    /usr/bin/ditto "$STAGED_APP" "$APP_PATH"
    /usr/bin/codesign --force --sign - "$APP_PATH"
else
    mv "$STAGED_APP" "$APP_PATH"
fi
/usr/bin/codesign --verify --strict --all-architectures "$APP_PATH"
printf 'Created: %s\n' "$APP_PATH"
/usr/bin/lipo -archs "$APP_PATH/Contents/MacOS/Dashboard Layout Editor"
