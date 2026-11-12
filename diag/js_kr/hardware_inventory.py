#!/usr/bin/env python3
"""JS hardware inventory; Wi-Fi activation is tested only with --try-enable."""
import argparse
import json
import re
import subprocess
import sys
import time

EXPECTED_BUILD = "eng.vmadmin.20260114.090656"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True, help="Exact ADB device serial")
    p.add_argument("--adb", default="adb")
    p.add_argument("--try-enable", action="store_true", help="Opt in to try Wi-Fi ON and restore OFF only when original OFF is confirmed")
    a = p.parse_args()

    def shell(*args):
        try:
            result = subprocess.run([a.adb, "-s", a.serial, "shell", *args],
                                    text=True, capture_output=True, timeout=20)
        except (OSError, subprocess.TimeoutExpired):
            return None
        if result.returncode:
            return None
        return result.stdout.replace("\r", "").strip()

    product = shell("getprop", "ro.product.name")
    build = shell("getprop", "ro.build.version.incremental")
    if product != "js_kr" or build != EXPECTED_BUILD:
        sys.exit("Target not verified as js_kr / " + EXPECTED_BUILD)

    keys = ("ro.product.model", "ro.product.board", "ro.hardware", "ro.board.platform",
            "ro.build.version.release", "ro.build.version.sdk", "ro.wifi.channels",
            "wifi.interface", "init.svc.wpa_supplicant")
    props = {key: shell("getprop", key) for key in keys}
    features = shell("pm", "list", "features")
    services = shell("service", "list")
    interfaces = shell("ls", "/sys/class/net")
    modules = shell("cat", "/proc/modules")
    cpu = shell("cat", "/proc/cpuinfo")
    def listing(path):
        result = shell("ls", path)
        if result is None or re.search(r"(permission denied|no such file|not found)", result, re.I):
            return None
        return result.splitlines()
    hardware = {"sysfs_classes": listing("/sys/class"),
                "input_devices": listing("/sys/class/input"),
                "audio_devices": listing("/sys/class/sound"),
                "video_devices": listing("/sys/class/video4linux"),
                "block_devices": listing("/sys/block"),
                "usb_nodes": listing("/sys/bus/usb/devices"),
                "cpu_description": [x for x in cpu.splitlines()
                                    if re.match(r"^(Hardware|Processor|model name|CPU architecture)\s*:", x, re.I)] if cpu else None}
    wifi = shell("dumpsys", "wifi")
    def confirmed_state(dump):
        if dump is None:
            return None
        found = re.search(r"\bwi-?fi\s+is\s+(enabled|disabled)\b", dump, re.I)
        return found.group(1).lower() if found else None

    original = confirmed_state(wifi)
    probe = {"requested": a.try_enable, "original_state": original}
    if a.try_enable and original == "disabled":
        try:
            probe["enable_command_output"] = shell("svc", "wifi", "enable")
            probe["observed"] = None
            for _ in range(8):
                time.sleep(1)
                probe["observed"] = confirmed_state(shell("dumpsys", "wifi"))
                if probe["observed"] == "enabled":
                    break
        finally:
            probe["restore_command_output"] = shell("svc", "wifi", "disable")
            probe["restored"] = None
            for _ in range(8):
                time.sleep(1)
                probe["restored"] = confirmed_state(shell("dumpsys", "wifi"))
                if probe["restored"] == "disabled":
                    break
    elif a.try_enable:
        probe["skipped"] = "Already enabled" if original == "enabled" else "Original state not confirmed; no toggle"
    state = []
    if wifi is not None:
        for line in wifi.splitlines():
            # Preserve only state words; never output SSID, MAC, addresses or credentials.
            m = re.search(r"\b(?:wi-?fi|wifistate)\b[^\n]{0,35}\b(enabled|disabled|enabling|disabling|unknown)\b", line, re.I)
            if m and m.group(1).lower() not in state:
                state.append(m.group(1).lower())
    output = {"product": product, "incremental": build, "properties": props,
              "hardware": hardware,
              "features": features.splitlines() if features is not None else None,
              "relevant_services": [x.strip() for x in services.splitlines()
                                    if re.search(r"wifi|micom|hscan|cluster|hud", x, re.I)] if services is not None else None,
              "network_interfaces": interfaces.splitlines() if interfaces is not None else None,
              "loaded_modules": [x.split()[0] for x in modules.splitlines() if x.split()] if modules is not None else None,
              "wifi_reported_states": state if wifi is not None else None,
              "wifi_enable_probe": probe,
              "note": "Read-only evidence. Feature/service/interface presence does not prove radio hardware, driver operation or ability to enable Wi-Fi."}
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
