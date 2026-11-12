#!/usr/bin/env python3
"""Capture raw hscan_mux socket bytes over temporary ADB forwarding, without an app."""
import argparse
import json
import socket
import subprocess
import sys
import time

BUILD = "eng.vmadmin.20260114.090656"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", required=True, help="Exact ADB serial; omitted from capture")
    p.add_argument("--out", required=True, help="New private JSONL file; never overwrites")
    p.add_argument("--seconds", type=int, default=60, choices=range(1, 601), metavar="1..600")
    p.add_argument("--max-mib", type=int, default=16, choices=range(1, 129), metavar="1..128")
    p.add_argument("--adb", default="adb")
    a = p.parse_args()
    adb = [a.adb, "-s", a.serial]

    def call(*args):
        return subprocess.run(adb + list(args), check=True, capture_output=True, text=True, timeout=20).stdout.strip().replace("\r", "")

    if call("shell", "getprop", "ro.product.name") != "js_kr" or call("shell", "getprop", "ro.build.version.incremental") != BUILD:
        sys.exit("Target is not the verified js_kr build")
    port = None
    with open(a.out, "x", encoding="utf-8") as out:
        try:
            port = int(call("forward", "tcp:0", "localabstract:hscan_mux"))
            out.write(json.dumps({"format": "js-hscan-stream-v1", "product": "js_kr", "build": BUILD,
                                  "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}) + "\n")
            total = seq = 0
            reason = "duration"
            deadline = time.monotonic() + a.seconds
            with socket.create_connection(("127.0.0.1", port), timeout=5) as stream:
                stream.settimeout(1)
                while time.monotonic() < deadline and total < a.max_mib * 1048576:
                    try:
                        chunk = stream.recv(min(4096, a.max_mib * 1048576 - total))
                    except socket.timeout:
                        continue
                    if not chunk:
                        reason = "socket_closed"
                        break
                    total += len(chunk); seq += 1
                    out.write(json.dumps({"seq": seq, "host_monotonic_ns": time.monotonic_ns(),
                                          "hex": chunk.hex()}) + "\n")
            if total >= a.max_mib * 1048576:
                reason = "size_limit"
            out.write(json.dumps({"end": True, "chunks": seq, "bytes": total,
                                  "reason": reason}) + "\n")
            if not total:
                sys.exit("No socket bytes received; preserve output and inspect connection and ignition state")
            print("Captured", total, "bytes in", seq, "chunks to", a.out, "reason:", reason)
        finally:
            if port is not None:
                try:
                    removed = subprocess.run(adb + ["forward", "--remove", "tcp:" + str(port)],
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
                    if removed.returncode:
                        print("Failed to remove ADB forward tcp:" + str(port), file=sys.stderr)
                except (OSError, subprocess.TimeoutExpired):
                    print("Could not confirm ADB forward removal tcp:" + str(port), file=sys.stderr)


if __name__ == "__main__":
    main()
