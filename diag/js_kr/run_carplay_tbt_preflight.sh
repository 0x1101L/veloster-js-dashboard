#!/bin/sh
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ] || [ -z "$1" ]; then
    echo "Usage: $0 ADB_SERIAL [inventory|events]" >&2
    exit 64
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MODE=${2:-inventory}
case "$MODE" in
    inventory)
        exec python3 "$SCRIPT_DIR/carplay_adb_probe.py" \
            --serial "$1" --check packages --check services
        ;;
    events)
        exec python3 "$SCRIPT_DIR/carplay_adb_probe.py" \
            --serial "$1" --check events-current --check events-previous
        ;;
    *)
        echo "Unknown mode: $MODE (expected inventory or events)" >&2
        exit 64
        ;;
esac
