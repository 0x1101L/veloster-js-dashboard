#!/usr/bin/env python3
"""Build and verify a signed JS Dashboard trial APK locally; never uses ADB."""
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SIGNER = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path, help="Compatible JDK java executable")
    parser.add_argument("--android-jar", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--key", required=True, type=Path, help="External public AOSP platform.pk8")
    parser.add_argument("--cert", required=True, type=Path, help="External public AOSP platform.x509.pem")
    args = parser.parse_args()

    pins = {entry["file"]: entry["sha256"] for entry in json.loads(
        (ROOT / "menu-entry/tool-sources.json").read_text(encoding="utf-8"))}
    key, cert = args.key.resolve(), args.cert.resolve()
    for name, path in (("platform.pk8", key), ("platform.x509.pem", cert)):
        if ROOT in path.parents or hashlib.sha256(path.read_bytes()).hexdigest() != pins[name]:
            raise SystemExit("Expected the pinned AOSP test file outside this repository: " + name)

    stamp = datetime.now(timezone(timedelta(hours=9))).strftime("%Y%m%d-%H%M%S")
    stem = "JS-Dashboard-build-" + stamp
    out, target = ROOT / "build" / stem, ROOT / "apks" / (stem + ".apk")
    tools, java = args.build_tools.resolve(), args.java.resolve()
    env = dict(os.environ, JAVA_HOME=str(java.parents[1]))
    if target.exists():
        raise SystemExit("Build artifact already exists: " + str(target))

    def run(*command, capture=False):
        return subprocess.run([str(part) for part in command], env=env, text=True,
                              stdout=subprocess.PIPE if capture else None, check=True).stdout

    run(sys.executable, ROOT / "scripts/build.py", "dashboard", "--java", java,
        "--android-jar", args.android_jar, "--aapt2", tools / "aapt2", "--r8-jar", tools / "lib/d8.jar", "--out", out)
    aligned, signed = out / "dashboard-aligned.apk", out / "dashboard-signed.apk"
    run(tools / "zipalign", "-f", "4", out / "dashboard-UNSIGNED.apk", aligned)
    run(tools / "apksigner", "sign", "--key", key, "--cert", cert, "--min-sdk-version", "17",
        "--v1-signer-name", "CERT", "--v1-signing-enabled", "true", "--v2-signing-enabled", "false",
        "--v3-signing-enabled", "false", "--v4-signing-enabled", "false", "--out", signed, aligned)
    run(tools / "zipalign", "-c", "4", signed)
    result = run(tools / "apksigner", "verify", "--min-sdk-version", "17", "--verbose", "--print-certs", signed, capture=True)
    if ("Verified using v1 scheme (JAR signing): true" not in result
            or re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$", result, re.M) != [SIGNER]):
        raise SystemExit("Signed APK failed the API 17 V1 signer check")
    print(result, end="")
    # Publish only the verified file and never replace an existing build.
    os.link(signed, target)
    print(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
