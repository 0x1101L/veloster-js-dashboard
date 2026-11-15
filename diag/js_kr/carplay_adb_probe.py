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


def adb_device_state(adb, serial):
    """Require an explicitly selected, online ADB device before shell checks."""
    try:
        result = subprocess.run([adb, "-s", serial, "get-state"],
                                capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"status": "error", "detail": type(exc).__name__}
    if result.returncode or result.stdout.strip() != "device":
        return {"status": "error", "detail": "selected device is not online"}
    return {"status": "ok", "value": "device"}


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


EVENT_LINE = re.compile(r"^HUD_EVENT v=1 elapsed_ms=(\d+) event=([a-zA-Z0-9._-]+) value=([a-zA-Z0-9._-]+)$")
EVENT_ALLOWLIST = {
    "binding.media.3": {"registered", "stopped", "permission_denied", "error"},
    "carplay.media.mode": {"active", "inactive"},
    "carplay.media.playback": {"playing", "paused"},
    "carplay.media.visibility": {"hidden", "visible"},
    "navigation.route.source": {"sticky", "broadcast"},
    "navigation.route.observer": {"registered"},
    "navigation.route.stopped": {"stopped"},
    "navigation.observer": {"registered"},
    "navigation.observer.stopped": {"stopped"},
    "navigation.route.observer.error": {"error"},
    "navigation.observer.error": {"error"},
    "navigation.route.close": {"error"},
    "navigation.observer.close": {"error"},
    "navigation.route.invalid": {"unavailable"},
    "navigation.state": {"unavailable"},
    "navigation.state.error": {"error"},
    "tbt.returned": {"returned_unverified"},
}


def event_value_allowed(event, value):
    if event in ("navigation.route_state", "navigation.state_destination"):
        return re.fullmatch(r"-?\d{1,8}", value) is not None
    if event == "binding.media.3":
        match = re.fullmatch(r"(received|processed|invalid)_(\d{1,12})", value)
        if not match:
            return False
        count = int(match.group(2))
        return count > 0 and count & (count - 1) == 0
    return value in EVENT_ALLOWLIST.get(event, set())


def event_log_check(adb, serial, filename):
    """Read only the bounded, non-raw app event log and return allowlisted records."""
    if filename not in ("hud-events.log", "hud-events.previous.log"):
        return {"status": "error", "detail": "unsupported event log"}
    result = adb_shell(adb, serial, "run-as", "com.veloster.dashboard", "cat",
                       "files/diagnostics/" + filename)
    if result["status"] != "ok":
        return result
    records = []
    for line in result["value"].splitlines():
        match = EVENT_LINE.fullmatch(line)
        if not match:
            continue
        elapsed, event, value = match.groups()
        if event_value_allowed(event, value):
            records.append({"elapsed_ms": int(elapsed), "event": event, "value": value})
    if not records:
        return {"status": "unknown", "detail": "no recognized event records; not evidence of TBT absence"}
    return {"status": "ok", "file": filename, "records": records,
            "note": "Allowlisted event summary only; no raw payload or personal metadata; not a TBT receiver."}


# New checks are opt-in unless explicitly included in DEFAULT_CHECKS.
CHECKS = {"packages": package_check, "services": service_check,
          "events-current": lambda adb, serial: event_log_check(adb, serial, "hud-events.log"),
          "events-previous": lambda adb, serial: event_log_check(adb, serial, "hud-events.previous.log")}
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
    device = adb_device_state(args.adb, args.serial)
    if device["status"] != "ok":
        print(json.dumps({"status": "target_unverified", "device": device},
                         ensure_ascii=False, indent=2))
        return 2
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
    checks = {}
    skipped = []
    for index, name in enumerate(names):
        checks[name] = CHECKS[name](args.adb, args.serial)
        if checks[name].get("status") != "ok" and not name.startswith("events-"):
            skipped = names[index + 1:]
            break
    output = {"timestamp_utc": datetime.now(timezone.utc).isoformat(), "target": verification,
              "checks": checks, "skipped_checks": skipped,
              "status": "complete" if len(checks) == len(names) and all(v["status"] == "ok" for v in checks.values()) else "incomplete",
              "limitations": "Read-only package/service inventory or allowlisted prior HUD_EVENT summaries only; no CarPlay session, iAP2 messages, navigation input, or HUD output is verified."}
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0 if output["status"] == "complete" else 1


if __name__ == "__main__":
    sys.exit(main())
