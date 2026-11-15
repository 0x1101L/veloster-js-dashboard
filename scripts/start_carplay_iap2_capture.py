#!/usr/bin/env python3
"""Attach one or two bounded iAP2 receive hooks, then eternalize them for USB swap."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import frida


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pid", type=int, help="PID of the verified process loading libiap2.so")
    parser.add_argument("target_output", help="absolute, process-writable path on the head unit")
    parser.add_argument("--mode", choices=("callback", "read", "both"), default="callback",
                        help="capture iAP2 callback chunks, libiap2-originated libc read chunks, or both")
    parser.add_argument(
        "--script",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "diag/js_kr/carplay_iap2_capture.js",
        help="capture agent source (defaults to this checkout)",
    )
    parser.add_argument("--timeout", type=float, default=15.0, help="USB Frida discovery timeout")
    args = parser.parse_args()
    if args.pid <= 0:
        parser.error("pid must be positive")
    if not args.target_output.startswith("/") or ".." in args.target_output.split("/"):
        parser.error("target_output must be an absolute path without parent segments")

    device = frida.get_usb_device(timeout=int(args.timeout * 1000))
    session = device.attach(args.pid)
    script = None
    try:
        script = session.create_script(args.script.read_text(encoding="utf-8"))
        script.load()
        started = script.exports_sync.start_capture(args.target_output, args.mode)
        # Keep the hook running in the target after this host USB session ends.
        script.eternalize()
        session.detach()
        print(json.dumps({"state": "capturing", "pid": args.pid, "capture": started, "host_session_detached": True}, indent=2))
        return 0
    except Exception:
        if script is not None:
            try:
                script.unload()
            except Exception:
                pass
        try:
            session.detach()
        except Exception:
            pass
        raise


if __name__ == "__main__":
    raise SystemExit(main())
