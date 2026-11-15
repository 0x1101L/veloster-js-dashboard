import io
import pathlib
import subprocess
import shutil
import unittest

from scripts.decode_carplay_iap2_capture import FILE_MAGIC, FOOTER, RECORD, read_capture


def capture(records, dropped=0, footer=True):
    data = bytearray(FILE_MAGIC)
    for sequence, timestamp, payload in records:
        data.extend(RECORD.pack(b"I2RX", sequence, timestamp, len(payload)))
        data.extend(payload)
    if footer:
        data.extend(FOOTER.pack(b"DROP", dropped, 0))
    return bytes(data)


class CarPlayIap2CaptureTests(unittest.TestCase):
    def test_agent_record_layout_decodes_from_node_mock(self):
        node = shutil.which("node")
        if not node:
            self.skipTest("node is unavailable")
        source = pathlib.Path(__file__).parents[1] / "diag/js_kr/carplay_iap2_capture.js"
        harness = r'''const fs=require('fs'),vm=require('vm');const ctx={rpc:{},Script:{bindWeak(){}},Uint8Array,ArrayBuffer,Date,Math,clearInterval,setInterval,Process:{},Module:{},Interceptor:{},File:function(){}};vm.createContext(ctx);vm.runInContext(fs.readFileSync(process.argv[1],'utf8'),ctx);const enc=vm.runInContext('encodeRecord({sequence:7,wallTimeMs:1234,data:new Uint8Array([0,255]).buffer})',ctx);process.stdout.write(Buffer.from(enc).toString('hex'));'''
        out = subprocess.check_output([node, "-e", harness, str(source)], text=True).strip()
        self.assertEqual(out, "493252580000000700000000000004d20000000200ff")
        read_harness = harness.replace("wallTimeMs:1234,data:", 'wallTimeMs:1234,source:"read",data:')
        read_out = subprocess.check_output([node, "-e", read_harness, str(source)], text=True).strip()
        self.assertEqual(read_out, "524452580000000700000000000004d20000000200ff")

    def test_preserves_payload_boundaries_and_reports_integrity(self):
        records, summary = read_capture(io.BytesIO(capture([(1, 1000, b"\xff\x00"), (2, 1004, b"\x52\x01")])))
        self.assertEqual([(r.source, r.sequence, r.wall_time_ms, r.payload, r.stream_offset) for r in records], [("callback", 1, 1000, b"\xff\x00", 0), ("callback", 2, 1004, b"\x52\x01", 2)])
        self.assertEqual(summary, {"records": 2, "callback_records": 2, "read_records": 0, "stream_bytes": 4, "sequence_gaps": 0, "dropped_footer": 0, "stop_reason": "user_stop", "complete_footer": True})

    def test_read_and_callback_sources_are_kept_distinct(self):
        data = FILE_MAGIC + RECORD.pack(b"RDRX", 1, 1000, 1) + b"r" + RECORD.pack(b"I2RX", 2, 1001, 1) + b"c" + FOOTER.pack(b"DROP", 0, 0)
        records, summary = read_capture(io.BytesIO(data))
        self.assertEqual([record.source for record in records], ["read", "callback"])
        self.assertEqual((summary["read_records"], summary["callback_records"]), (1, 1))

    def test_reports_sequence_gap_and_drop_footer(self):
        records, summary = read_capture(io.BytesIO(capture([(1, 1000, b"a"), (4, 1001, b"b")], dropped=3)))
        self.assertEqual([r.sequence for r in records], [1, 4])
        self.assertEqual(summary["sequence_gaps"], 2)
        self.assertEqual(summary["dropped_footer"], 3)

    def test_missing_footer_is_incomplete(self):
        records, summary = read_capture(io.BytesIO(capture([(1, 1000, b"a")], footer=False)))
        self.assertEqual([r.payload for r in records], [b"a"])
        self.assertFalse(summary["complete_footer"])

    def test_rejects_truncated_payload(self):
        data = FILE_MAGIC + RECORD.pack(b"I2RX", 1, 1000, 4) + b"xy"
        records, _ = read_capture(io.BytesIO(data))
        with self.assertRaisesRegex(ValueError, "truncated record payload"):
            list(records)

    def test_rejects_out_of_order_sequence(self):
        records, _ = read_capture(io.BytesIO(capture([(2, 1000, b"a"), (1, 1001, b"b")])))
        with self.assertRaisesRegex(ValueError, "not increasing"):
            list(records)


if __name__ == "__main__":
    unittest.main()
