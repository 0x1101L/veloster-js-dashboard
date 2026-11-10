#!/usr/bin/env python3
"""Extract structured JS telemetry samples from logcat, offline or via read-only ADB.

The script never changes device state or project findings. It emits CSV, a JSON
summary and a draft for an agent to review.
"""
import argparse
import csv
import json
import math
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys

EXPECTED_PROFILE = "js_kr"
EXPECTED_BUILD = "eng.vmadmin.20260114.090656"
MARKER = "JS_SAMPLE "
FIELDS = ("t_ms", "profile", "build", "ign", "signal", "source",
          "value", "unit", "valid", "age_ms")
TOKEN = re.compile(r"([a-z_]+)=([^\s]+)")


def parse_sample(line, line_number):
    marker_at = line.find(MARKER)
    if marker_at < 0:
        return None
    payload = line[marker_at + len(MARKER):].strip()
    pairs = TOKEN.findall(payload)
    fields = dict(pairs)
    if len(fields) != len(pairs):
        raise ValueError("line %d: duplicate sample field" % line_number)
    missing = [field for field in FIELDS if field not in fields]
    extra = sorted(set(fields) - set(FIELDS))
    if missing or extra:
        raise ValueError("line %d: missing=%s extra=%s" %
                         (line_number, ",".join(missing), ",".join(extra)))
    if fields["profile"] != EXPECTED_PROFILE or fields["build"] != EXPECTED_BUILD:
        raise ValueError("line %d: unexpected profile/build %s/%s" %
                         (line_number, fields["profile"], fields["build"]))
    if fields["ign"] not in ("true", "false") or fields["valid"] not in ("true", "false"):
        raise ValueError("line %d: ign and valid must be true or false" % line_number)
    try:
        fields["t_ms"] = int(fields["t_ms"])
        fields["age_ms"] = int(fields["age_ms"])
        fields["value"] = float(fields["value"])
    except ValueError as error:
        raise ValueError("line %d: invalid numeric field" % line_number) from error
    if fields["t_ms"] < 0 or fields["age_ms"] < 0 or not math.isfinite(fields["value"]):
        raise ValueError("line %d: negative time/age or non-finite value" % line_number)
    if not re.fullmatch(r"[a-z0-9_]+", fields["signal"]):
        raise ValueError("line %d: signal must be a lowercase identifier" % line_number)
    if not re.fullmatch(r"[a-z0-9_]+", fields["source"]):
        raise ValueError("line %d: source must be a lowercase identifier" % line_number)
    return fields


def extract_lines(lines, output_dir, since_t_ms=None):
    rows = []
    for number, line in enumerate(lines, 1):
        row = parse_sample(line, number)
        if row is not None and (since_t_ms is None or row["t_ms"] >= since_t_ms):
            rows.append(row)
    if not rows:
        raise ValueError("no JS_SAMPLE records found; this app/log does not expose numeric telemetry yet")
    rows.sort(key=lambda row: (row["t_ms"], row["signal"]))
    output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = output_dir / "js_samples.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "profile": EXPECTED_PROFILE,
        "build": EXPECTED_BUILD,
        "sample_rows": len(rows),
        "elapsed_ms": rows[-1]["t_ms"] - rows[0]["t_ms"],
        "signals": {},
    }
    for signal in sorted({row["signal"] for row in rows}):
        group = [row for row in rows if row["signal"] == signal]
        valid = [row for row in group if row["valid"] == "true"]
        values = [row["value"] for row in valid]
        units = sorted({row["unit"] for row in group})
        sources = sorted({row["source"] for row in group})
        if len(units) != 1 or len(sources) != 1:
            raise ValueError("signal %s changes unit or source within one capture" % signal)
        summary["signals"][signal] = {
            "unit": units[0], "source": sources[0], "rows": len(group),
            "valid_rows": len(valid), "invalid_rows": len(group) - len(valid),
            "min": min(values) if values else None,
            "max": max(values) if values else None,
            "mean": statistics.fmean(values) if values else None,
            "first_t_ms": group[0]["t_ms"], "last_t_ms": group[-1]["t_ms"],
            "max_age_ms": max(row["age_ms"] for row in group),
        }
    (output_dir / "js_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    note = ["# JS 수집값 검토 초안", "",
            "자동 추출 요약입니다. 차량 기준값과의 비교나 지원 판정은 하지 않았습니다.",
            "에이전트는 조사 조건과 독립 비교 근거를 확인한 뒤 JS_DEBUGGING.md 및 신호 상태표를 갱신하세요.", "",
            "- 대상 프로필/빌드: %s / %s" % (EXPECTED_PROFILE, EXPECTED_BUILD),
            "- 추출 행 수: %d" % len(rows), "- 기록 구간: %d ms" % summary["elapsed_ms"], "",
            "| 신호 | 입력 경로 | 단위 | 유효/전체 | 최솟값 | 최댓값 | 평균 | 최대 age (ms) |",
            "|---|---|---:|---:|---:|---:|---:|---:|"]
    for signal, values in summary["signals"].items():
        def fmt(value):
            return "미수집" if value is None else "%.4f" % value
        note.append("| %s | %s | %s | %d/%d | %s | %s | %s | %d |" % (
            signal, values["source"], values["unit"], values["valid_rows"], values["rows"],
            fmt(values["min"]), fmt(values["max"]), fmt(values["mean"]), values["max_age_ms"]))
    note += ["", "비교 기준·운행 조건·관측자 메모는 조사자가 채우세요. 이 요약만으로 실차 검증 완료로 판정하지 마세요.", ""]
    (output_dir / "agent_review.md").write_text("\n".join(note), encoding="utf-8")
    return len(rows)


def extract(input_path, output_dir):
    with input_path.open("r", encoding="utf-8", errors="replace") as source:
        return extract_lines(source, output_dir)


def adb_capture(serial, seconds):
    adb = shutil.which("adb")
    if not adb:
        raise ValueError("adb executable not found")

    def run(command):
        result = subprocess.run([adb, "-s", serial] + command, capture_output=True,
                                text=True, timeout=15)
        if result.returncode:
            raise ValueError("ADB command failed: %s" % command[0])
        return result.stdout.strip()

    product = run(["shell", "getprop", "ro.product.name"])
    build = run(["shell", "getprop", "ro.build.version.incremental"])
    if product != EXPECTED_PROFILE or build != EXPECTED_BUILD:
        raise ValueError("device profile/build mismatch: %s/%s" % (product or "unknown", build or "unknown"))
    uptime = run(["shell", "cat", "/proc/uptime"]).split()
    if not uptime:
        raise ValueError("could not read device uptime; refusing an unbounded historical capture")
    since_t_ms = int(float(uptime[0]) * 1000)
    command = [adb, "-s", serial, "logcat", "-v", "brief", "-s", "VehicleDash:I", "*:S"]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               text=True, encoding="utf-8", errors="replace")
    try:
        stdout, _ = process.communicate(timeout=seconds)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            stdout, _ = process.communicate(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            stdout, _ = process.communicate()
    return stdout.splitlines(), since_t_ms


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", type=Path, help="read-only logcat export containing JS_SAMPLE lines")
    source.add_argument("--serial", help="capture live VehicleDash logs from this exact ADB serial")
    parser.add_argument("--seconds", type=int, default=60, help="live ADB capture duration, 1 to 600 seconds (default: 60)")
    parser.add_argument("--out-dir", required=True, type=Path, help="directory for CSV, summary and review draft")
    args = parser.parse_args()
    try:
        if not 1 <= args.seconds <= 600:
            raise ValueError("--seconds must be between 1 and 600")
        if args.serial:
            lines, since_t_ms = adb_capture(args.serial, args.seconds)
            count = extract_lines(lines, args.out_dir, since_t_ms)
        else:
            count = extract(args.input, args.out_dir)
    except (OSError, ValueError) as error:
        print("ERROR: %s" % error, file=sys.stderr)
        return 2
    print("Extracted %d JS telemetry rows to %s" % (count, args.out_dir))
    print("Review agent_review.md against the investigation conditions before updating project findings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
