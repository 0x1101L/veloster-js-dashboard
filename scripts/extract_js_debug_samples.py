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
JS_LOGGER_VERSION = 1
LOGGER_MARKER = "JS_LOGGER "
LOGGER_FIELDS = ("v", "profile", "build", "t_ms", "type", "name", "source", "status",
                 "value", "unit", "received_ms", "received_age_ms", "valid_ms",
                 "valid_age_ms", "count")
LOGGER_STATUSES = {"missing", "valid", "invalid", "held", "expired", "unavailable",
                   "unknown", "registered", "stopped", "connecting", "connected",
                   "disconnected", "unsupported", "error", "permission_denied", "absent",
                   "present", "enabled", "disabled", "enabling", "disabling", "observed"}


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


def parse_logger(line, line_number):
    marker_at = line.find(LOGGER_MARKER)
    if marker_at < 0:
        return None
    pairs = TOKEN.findall(line[marker_at + len(LOGGER_MARKER):].strip())
    fields = dict(pairs)
    if len(fields) != len(pairs):
        raise ValueError("line %d: duplicate JS_LOGGER field" % line_number)
    missing = [field for field in LOGGER_FIELDS if field not in fields]
    extra = sorted(set(fields) - set(LOGGER_FIELDS))
    if missing or extra:
        raise ValueError("line %d: JS_LOGGER missing=%s extra=%s" %
                         (line_number, ",".join(missing), ",".join(extra)))
    if fields["profile"] != EXPECTED_PROFILE or fields["build"] != EXPECTED_BUILD:
        raise ValueError("line %d: unexpected profile/build %s/%s" %
                         (line_number, fields["profile"], fields["build"]))
    try:
        fields["v"] = int(fields["v"])
        fields["t_ms"] = int(fields["t_ms"])
        fields["received_ms"] = int(fields["received_ms"])
        fields["received_age_ms"] = int(fields["received_age_ms"])
        fields["valid_ms"] = int(fields["valid_ms"])
        fields["valid_age_ms"] = int(fields["valid_age_ms"])
        fields["count"] = int(fields["count"])
    except ValueError as error:
        raise ValueError("line %d: invalid JS_LOGGER integer" % line_number) from error
    if fields["v"] != JS_LOGGER_VERSION:
        raise ValueError("line %d: unsupported JS_LOGGER version %s (expected %d)" %
                         (line_number, fields["v"], JS_LOGGER_VERSION))
    if fields["t_ms"] < 0 or fields["count"] < 0 or any(
            fields[key] < -1 for key in ("received_ms", "received_age_ms", "valid_ms", "valid_age_ms")):
        raise ValueError("line %d: negative JS_LOGGER time or count" % line_number)
    if fields["status"] not in LOGGER_STATUSES:
        raise ValueError("line %d: unsupported JS_LOGGER status" % line_number)
    if fields["type"] not in ("signal", "diagnostic"):
        raise ValueError("line %d: unsupported JS_LOGGER record type" % line_number)
    if not re.fullmatch(r"[a-z][a-z0-9_.]*", fields["name"]):
        raise ValueError("line %d: invalid JS_LOGGER name" % line_number)
    if not re.fullmatch(r"[a-z][a-z0-9_.]*", fields["source"]):
        raise ValueError("line %d: invalid JS_LOGGER source" % line_number)
    for timestamp, age in (("received_ms", "received_age_ms"), ("valid_ms", "valid_age_ms")):
        if (fields[timestamp] == -1) != (fields[age] == -1):
            raise ValueError("line %d: JS_LOGGER timestamp/age mismatch" % line_number)
        if fields[timestamp] >= 0 and (fields[timestamp] > fields["t_ms"] or
                                       fields[timestamp] + fields[age] != fields["t_ms"]):
            raise ValueError("line %d: inconsistent JS_LOGGER monotonic time" % line_number)
    if fields["type"] == "signal" and fields["received_ms"] == -1 and fields["count"] != 0:
        raise ValueError("line %d: JS_LOGGER unseen record has a receive count" % line_number)
    if fields["value"] == "na":
        fields["value"] = None
    else:
        try:
            fields["value"] = float(fields["value"])
        except ValueError as error:
            raise ValueError("line %d: invalid JS_LOGGER value" % line_number) from error
        if not math.isfinite(fields["value"]):
            raise ValueError("line %d: non-finite JS_LOGGER value" % line_number)
    if fields["unit"] == "" or not re.fullmatch(r"[a-zA-Z0-9_%°/·.-]+", fields["unit"]):
        raise ValueError("line %d: invalid JS_LOGGER unit" % line_number)
    if fields["type"] == "signal":
        if fields["status"] not in {"missing", "valid", "invalid", "held", "expired"}:
            raise ValueError("line %d: invalid JS_LOGGER signal status" % line_number)
        if fields["status"] in {"valid", "held"}:
            if fields["value"] is None or fields["valid_ms"] < 0 or fields["received_ms"] < 0:
                raise ValueError("line %d: JS_LOGGER valid/held signal lacks a value or timestamp" % line_number)
        elif fields["value"] is not None:
            raise ValueError("line %d: JS_LOGGER non-current signal status must not expose a value" % line_number)
    return fields


def extract_lines(lines, output_dir, since_t_ms=None):
    rows, logger_rows = [], []
    for number, line in enumerate(lines, 1):
        logger_row = parse_logger(line, number)
        row = parse_sample(line, number) if logger_row is None else None
        if logger_row is not None and (since_t_ms is None or logger_row["t_ms"] >= since_t_ms):
            logger_rows.append(logger_row)
        if row is not None and (since_t_ms is None or row["t_ms"] >= since_t_ms):
            rows.append(row)
    if not rows and not logger_rows:
        raise ValueError("no JS_SAMPLE or JS_LOGGER records found; this app/log does not expose numeric telemetry yet")
    output_dir.mkdir(parents=True, exist_ok=True)
    if rows:
        rows.sort(key=lambda row: (row["t_ms"], row["signal"]))
        _write_samples(rows, output_dir)
    if logger_rows:
        logger_rows.sort(key=lambda row: (row["t_ms"], row["type"], row["name"]))
        _write_logger(logger_rows, output_dir)
    return len(rows) + len(logger_rows)


def _write_samples(rows, output_dir):
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
            "에이전트는 조사 조건과 독립 비교 근거를 확인한 뒤 js_debugging.md 및 신호 상태표를 갱신하세요.", "",
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


def _write_logger(rows, output_dir):
    csv_path = output_dir / "js_logger.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=LOGGER_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    summary = {"contract_version": JS_LOGGER_VERSION, "profile": EXPECTED_PROFILE,
               "build": EXPECTED_BUILD, "record_rows": len(rows),
               "elapsed_ms": rows[-1]["t_ms"] - rows[0]["t_ms"], "records": {}}
    keys = sorted({(row["type"], row["name"], row["source"], row["unit"]) for row in rows})
    for kind, name, source, unit in keys:
        group = [row for row in rows if (row["type"], row["name"], row["source"], row["unit"]) ==
                 (kind, name, source, unit)]
        valid = [row["value"] for row in group if row["type"] == "signal" and
                 row["status"] == "valid" and row["value"] is not None]
        summary["records"]["%s/%s/%s/%s" % (kind, name, source, unit)] = {
            "type": kind, "name": name, "source": source, "unit": unit,
            "rows": len(group), "statuses": {status: sum(row["status"] == status for row in group)
                                                for status in sorted({r["status"] for r in group})},
            "valid_min": min(valid) if valid else None,
            "valid_max": max(valid) if valid else None,
            "valid_mean": statistics.fmean(valid) if valid else None,
            "first_t_ms": group[0]["t_ms"], "last_t_ms": group[-1]["t_ms"],
        }
    (output_dir / "js_logger_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    note = ["# JS_LOGGER 수집 분석 초안", "",
            "이 결과는 앱이 기록한 상태와 보관값의 통계다. 단위·출처 정확성, 신호 지원 또는 차량 호환성을 입증하지 않는다.",
            "원시 OEM payload, 목적지·좌표, 미디어 정보 및 기기 식별자는 입력 계약에 포함되지 않는다.", "",
            "- 계약 버전: %d" % JS_LOGGER_VERSION,
            "- 프로필/빌드: %s / %s" % (EXPECTED_PROFILE, EXPECTED_BUILD),
            "- 기록 행/구간: %d / %d ms" % (len(rows), summary["elapsed_ms"]), "",
            "| 종류/이름 | 출처 | 단위 | 상태별 행 수 | 유효 값 범위/평균 |",
            "|---|---|---|---|---|"]
    for record in summary["records"].values():
        status_text = ", ".join("%s=%d" % item for item in record["statuses"].items())
        values = "미기록" if record["valid_min"] is None else "%.4f / %.4f / %.4f" % (
            record["valid_min"], record["valid_max"], record["valid_mean"])
        note.append("| %s/%s | %s | %s | %s | %s |" % (
            record["type"], record["name"], record["source"], record["unit"], status_text, values))
    note += ["", "`unknown` 출처는 원본 경로를 확인할 수 없다는 뜻이다. 미수신·무효·보관·만료 상태를 합산해 유효값으로 취급하지 않는다.",
             "차량 계기와의 비교 및 운행 조건은 조사자가 확인한 경우에만 별도 기록한다.", ""]
    (output_dir / "js_logger_analysis.md").write_text("\n".join(note), encoding="utf-8")


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
    command = [adb, "-s", serial, "logcat", "-v", "brief", "-s", "JSLogger:I", "DashboardView:I", "*:S"]
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
    source.add_argument("--input", type=Path, help="read-only logcat export containing JS_LOGGER or legacy JS_SAMPLE lines")
    source.add_argument("--serial", help="capture JSLogger and legacy DashboardView records from this exact ADB serial")
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
    print("Review js_logger_analysis.md and/or agent_review.md against the investigation conditions before updating project findings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
