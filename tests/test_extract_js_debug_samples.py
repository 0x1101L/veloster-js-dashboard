import csv
import json
from pathlib import Path
import tempfile
import unittest

from scripts.extract_js_debug_samples import extract, parse_sample


def sample(t_ms, signal, value, valid="true", age="12"):
    return ("11-10 12:00:00.000 I/VehicleDash( 123): JS_SAMPLE "
            "t_ms=%d profile=js_kr build=eng.vmadmin.20260114.090656 ign=true "
            "signal=%s source=nmode value=%s unit=rpm valid=%s age_ms=%s" %
            (t_ms, signal, value, valid, age))


class JsDebugSamplesTest(unittest.TestCase):
    def test_parse_exact_js_sample(self):
        row = parse_sample(sample(100, "rpm", "812.25"), 1)
        self.assertEqual(row["profile"], "js_kr")
        self.assertEqual(row["value"], 812.25)
        self.assertTrue(row["valid"] == "true")

    def test_rejects_wrong_vehicle_build(self):
        line = sample(100, "rpm", "812").replace("js_kr", "jsn_kr")
        with self.assertRaisesRegex(ValueError, "unexpected profile/build"):
            parse_sample(line, 3)

    def test_extracts_csv_summary_and_agent_draft(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "logcat.txt"
            output = root / "out"
            source.write_text("unrelated line\n" + sample(100, "rpm", "800") + "\n" +
                              sample(1100, "rpm", "900", age="20") + "\n", encoding="utf-8")
            self.assertEqual(extract(source, output), 2)
            with (output / "js_samples.csv").open(encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(len(rows), 2)
            summary = json.loads((output / "js_summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["elapsed_ms"], 1000)
            self.assertEqual(summary["signals"]["rpm"]["max"], 900.0)
            draft = (output / "agent_review.md").read_text(encoding="utf-8")
            self.assertIn("비교나 지원 판정은 하지 않았습니다", draft)
            self.assertIn("신호 상태표를 갱신하세요", draft)

    def test_no_structured_values_is_explicit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "logcat.txt"
            source.write_text("I/VehicleDash: 0:2/50ms/valid\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "does not expose numeric telemetry"):
                extract(source, root / "out")


if __name__ == "__main__":
    unittest.main()
