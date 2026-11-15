#!/usr/bin/env python3
"""Validate and optionally flatten a CPRAW1 receive capture without altering it."""

from __future__ import annotations

import argparse
import json
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Iterator

FILE_MAGIC = b"CPRAW1\r\n"
RECORD = struct.Struct(">4sIQI")
FOOTER = struct.Struct(">4sII")
RECORD_MAGIC = b"I2RX"
READ_MAGIC = b"RDRX"
FOOTER_MAGIC = b"DROP"
MAX_RECORD_BYTES = 64 * 1024
STOP_REASONS = {0: "user_stop", 1: "duration_limit", 2: "file_limit", 3: "write_failure"}


@dataclass(frozen=True)
class CaptureRecord:
    source: str
    sequence: int
    wall_time_ms: int
    payload: bytes
    stream_offset: int


def read_capture(stream: BinaryIO) -> tuple[Iterator[CaptureRecord], dict[str, int | bool | str]]:
    if stream.read(len(FILE_MAGIC)) != FILE_MAGIC:
        raise ValueError("not a CPRAW1 capture")
    summary: dict[str, int | bool] = {
        "records": 0,
        "stream_bytes": 0,
        "sequence_gaps": 0,
        "dropped_footer": 0,
        "stop_reason": "missing_footer",
        "complete_footer": False,
    }

    def records() -> Iterator[CaptureRecord]:
        offset = 0
        previous_sequence = 0
        gap_count = 0
        count = 0
        callback_count = 0
        read_count = 0
        dropped = 0
        stop_reason = -1
        complete = False
        while True:
            marker = stream.read(4)
            if marker == b"":
                break
            if marker == FOOTER_MAGIC:
                remainder = stream.read(FOOTER.size - 4)
                if len(remainder) != FOOTER.size - 4:
                    raise ValueError("truncated DROP footer")
                _, dropped, stop_reason = FOOTER.unpack(marker + remainder)
                if stop_reason not in STOP_REASONS:
                    raise ValueError(f"unknown stop reason {stop_reason}")
                if stream.read(1):
                    raise ValueError("trailing bytes after DROP footer")
                complete = True
                break
            if marker not in (RECORD_MAGIC, READ_MAGIC):
                raise ValueError(f"unexpected record marker at record {count + 1}: {marker!r}")
            rest = stream.read(RECORD.size - 4)
            if len(rest) != RECORD.size - 4:
                raise ValueError("truncated record header")
            _, sequence, wall_time_ms, length = RECORD.unpack(marker + rest)
            if length == 0 or length > MAX_RECORD_BYTES:
                raise ValueError(f"invalid payload length {length}")
            payload = stream.read(length)
            if len(payload) != length:
                raise ValueError("truncated record payload")
            if sequence <= previous_sequence:
                raise ValueError("record sequence is not increasing")
            if previous_sequence and sequence > previous_sequence + 1:
                gap_count += sequence - previous_sequence - 1
            previous_sequence = sequence
            source = "callback" if marker == RECORD_MAGIC else "read"
            if source == "callback":
                callback_count += 1
            else:
                read_count += 1
            record = CaptureRecord(source, sequence, wall_time_ms, payload, offset)
            offset += length
            count += 1
            yield record
        summary.update(
            {
                "records": count,
                "callback_records": callback_count,
                "read_records": read_count,
                "stream_bytes": offset,
                "sequence_gaps": gap_count,
                "dropped_footer": dropped,
                "stop_reason": STOP_REASONS.get(stop_reason, "missing_footer"),
                "complete_footer": complete,
            }
        )

    return records(), summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path, help="original CPRAW1 capture file")
    parser.add_argument(
        "--payload-out",
        type=Path,
        help="create a new concatenated receive-stream file (never overwrite)",
    )
    args = parser.parse_args()

    payload_file = None
    try:
        with args.capture.open("rb") as source:
            records, summary = read_capture(source)
            index = []
            if args.payload_out:
                payload_file = args.payload_out.open("xb")
            for record in records:
                if payload_file:
                    payload_file.write(record.payload)
                index.append(
                    {
                        "sequence": record.sequence,
                        "source": record.source,
                        "wall_time_ms": record.wall_time_ms,
                        "length": len(record.payload),
                        "stream_offset": record.stream_offset,
                    }
                )
            summary = dict(summary)
        if payload_file:
            payload_file.close()
        print(json.dumps({"summary": summary, "records": index, "payload_out": str(args.payload_out) if args.payload_out else None}, indent=2))
        return 0 if summary["complete_footer"] and summary["dropped_footer"] == 0 and summary["sequence_gaps"] == 0 else 2
    except (OSError, ValueError) as error:
        if payload_file:
            payload_file.close()
        parser.error(str(error))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
