#!/usr/bin/env python3
"""Read-only JS CarPlay diagnostic inventory with selectable checks."""
import argparse
from datetime import datetime, timezone
import json
import re
import subprocess
import sys

EXPECTED_PRODUCT = "js_kr"
EXPECTED_BUILD = "eng.vmadmin.20260114.090656"
EXPECTED_API = "17"
ERROR_TEXT = re.compile(r"permission denied|securityexception|not found|unknown command|error:|can't find service", re.I)


def adb_shell(adb, serial, *command):
    try:
        result = subprocess.run([adb, "-s", serial, "shell", *command],
                                capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"status": "error", "detail": type(exc).__name__}
    output = result.stdout.replace("\r", "").strip()
    if result.returncode or ERROR_TEXT.search(output) or ERROR_TEXT.search(result.stderr):
        status = "denied" if re.search(r"permission denied|securityexception", output + result.stderr, re.I) else "error"
        return {"status": status, "detail": "adb or remote command failed"}
    if not output:
        return {"status": "unknown", "detail": "empty response"}
    return {"status": "ok", "value": output}


def package_check(adb, serial):
    result = adb_shell(adb, serial, "pm", "list", "packages")
    if result["status"] != "ok":
        return result
    packages = set(re.findall(r"^package:([^\s]+)$", result["value"], re.M))
    if not packages:
        return {"status": "unknown", "detail": "no recognizable package entries"}
    targets = ("com.daudio.app.dipo", "com.tcc.iap2server", "com.veloster.dashboard")
    return {"status": "ok", "packages": {name: name in packages for name in targets},
            "note": "Package presence is not evidence of a running process or navigation data."}


def service_check(adb, serial):
    result = adb_shell(adb, serial, "service", "list")
    if result["status"] != "ok":
        return result
    lines = result["value"].splitlines()
    # Android 4.2 service list uses a numeric index followed by a service name.
    found = re.findall(r"^\s*\d+\s+([^:\s]+):", "\n".join(lines), re.M)
    if not found:
        return {"status": "unknown", "detail": "unrecognized service list"}
    targets = ("com.hkmc.micom.cluster",)
    return {"status": "ok", "services": {name: name in found for name in targets},
            "note": "Service presence is not evidence of permission, TBT input, or HUD output."}


def raw_log_check(adb, serial, filename):
    # Explicit selection only: this returns sensitive raw content to stdout.
    result = adb_shell(adb, serial, "run-as", "com.veloster.dashboard", "cat", "files/diagnostics/" + filename)
    if result["status"] != "ok":
        return result
    lines = result["value"].splitlines()
    if not lines or any(not line.startswith("CARPLAY_RAW v=1 ") for line in lines):
        return {"status": "error", "detail": "unrecognized or incomplete raw log"}
    return {"status": "ok", "file": filename, "records": lines,
            "note": "Sensitive raw values; partial writes are possible; not proof of navigation input."}


def raw_current(adb, serial):
    return raw_log_check(adb, serial, "carplay-raw.log")


def raw_previous(adb, serial):
    return raw_log_check(adb, serial, "carplay-raw.previous.log")


# New checks are opt-in unless explicitly included in DEFAULT_CHECKS.
CHECKS = {"packages": package_check, "services": service_check,
          "raw-current": raw_current, "raw-previous": raw_previous}
DEFAULT_CHECKS = ("packages", "services")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="Exact serial of the parked JS device; never printed")
    parser.add_argument("--adb", default="adb", help="ADB executable")
    parser.add_argument("--check", action="append", choices=tuple(CHECKS), help="Run a named check; repeat to select multiple")
    parser.add_argument("--list", action="store_true", help="List checks without connecting")
    args = parser.parse_args(argv)
    if args.list:
        print("\n".join(CHECKS))
        return 0
    if not args.serial:
        parser.error("--serial is required unless --list is used")
    props = (("product", "ro.product.name", EXPECTED_PRODUCT),
             ("build", "ro.build.version.incremental", EXPECTED_BUILD),
             ("api", "ro.build.version.sdk", EXPECTED_API))
    verification = {}
    for name, key, expected in props:
        result = adb_shell(args.adb, args.serial, "getprop", key)
        verification[name] = result
        if result.get("status") != "ok" or result.get("value") != expected:
            print(json.dumps({"status": "target_unverified", "target": verification}, ensure_ascii=False, indent=2))
            return 2
    names = list(dict.fromkeys(args.check or DEFAULT_CHECKS))
    checks = {name: CHECKS[name](args.adb, args.serial) for name in names}
    output = {"timestamp_utc": datetime.now(timezone.utc).isoformat(), "target": verification,
              "checks": checks, "status": "complete" if all(v["status"] == "ok" for v in checks.values()) else "incomplete",
              "limitations": "Inventory only; no CarPlay session, iAP2 messages, navigation input, or HUD output is verified."}
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0 if output["status"] == "complete" else 1


if __name__ == "__main__":
    sys.exit(main())
