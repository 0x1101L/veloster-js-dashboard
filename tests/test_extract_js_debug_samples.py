import csv
import json
from pathlib import Path
import tempfile
import unittest
import re

from scripts.extract_js_debug_samples import JS_LOGGER_VERSION, extract, parse_logger, parse_sample


def sample(t_ms, signal, value, valid="true", age="12"):
    return ("11-10 12:00:00.000 I/DashboardView( 123): JS_SAMPLE "
            "t_ms=%d profile=js_kr build=eng.vmadmin.20260114.090656 ign=true "
            "signal=%s source=nmode value=%s unit=rpm valid=%s age_ms=%s" %
            (t_ms, signal, value, valid, age))


def logger(t_ms=1000, name="oil_temp", status="held", value="5.0", source="nmode",
           received_ms=995, received_age_ms=5, valid_ms=990, valid_age_ms=10, count=2):
    return ("11-13 02:00:00.000 I/JSLogger( 123): JS_LOGGER v=1 profile=js_kr "
            "build=eng.vmadmin.20260114.090656 t_ms=%d type=signal name=%s source=%s "
            "status=%s value=%s unit=C received_ms=%d received_age_ms=%d valid_ms=%d "
            "valid_age_ms=%d count=%d" %
            (t_ms, name, source, status, value, received_ms, received_age_ms,
             valid_ms, valid_age_ms, count))


class JsDebugSamplesTest(unittest.TestCase):
    def test_logger_provider_notification_source(self):
        line = ("JS_LOGGER v=1 profile=js_kr build=eng.vmadmin.20260114.090656 "
                "t_ms=1000 type=diagnostic name=navigation_notice source=navigation_provider "
                "status=valid value=na unit=none received_ms=990 received_age_ms=10 "
                "valid_ms=-1 valid_age_ms=-1 count=1")
        self.assertEqual(parse_logger(line, 1)["source"], "navigation_provider")

    def test_android_and_python_share_logger_contract_version(self):
        java = Path(__file__).parents[1] / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsLogger.java"
        match = re.search(r"CONTRACT_VERSION\s*=\s*(\d+)", java.read_text(encoding="utf-8"))
        self.assertIsNotNone(match)
        self.assertEqual(int(match.group(1)), JS_LOGGER_VERSION)

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
            source.write_text("I/DashboardView: 0:2/50ms/valid\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "does not expose numeric telemetry"):
                extract(source, root / "out")

    def test_parse_versioned_logger_and_separate_clocks(self):
        row = parse_logger(logger(), 7)
        self.assertEqual(row["v"], 1)
        self.assertEqual(row["source"], "nmode")
        self.assertEqual(row["value"], 5.0)
        self.assertEqual(row["received_ms"] + row["received_age_ms"], row["t_ms"])
        self.assertEqual(row["valid_ms"] + row["valid_age_ms"], row["t_ms"])

    def test_rejects_unsupported_contract_and_inconsistent_clocks(self):
        with self.assertRaisesRegex(ValueError, "unsupported JS_LOGGER version"):
            parse_logger(logger().replace("v=1", "v=2"), 1)
        with self.assertRaisesRegex(ValueError, "inconsistent JS_LOGGER monotonic time"):
            parse_logger(logger().replace("received_ms=995", "received_ms=994"), 1)

    def test_logger_capture_writes_csv_summary_and_analysis_and_keeps_legacy_outputs(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "mixed.log"
            output = root / "out"
            source.write_text(logger() + "\n" + sample(100, "rpm", "800") + "\n", encoding="utf-8")
            self.assertEqual(extract(source, output), 2)
            with (output / "js_logger.csv").open(encoding="utf-8", newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(rows[0]["source"], "nmode")
            summary = json.loads((output / "js_logger_summary.json").read_text(encoding="utf-8"))
            record = summary["records"]["signal/oil_temp/nmode/C"]
            self.assertEqual(record["statuses"], {"held": 1})
            self.assertTrue((output / "js_logger_analysis.md").exists())
            self.assertTrue((output / "js_samples.csv").exists())
            self.assertTrue((output / "js_summary.json").exists())

    def test_logger_empty_unknown_source_and_invalid_value_are_explicit(self):
        text = logger(status="missing", value="na", source="unknown", received_ms=-1,
                      received_age_ms=-1, valid_ms=-1, valid_age_ms=-1, count=0)
        row = parse_logger(text, 1)
        self.assertIsNone(row["value"])
        self.assertEqual(row["status"], "missing")
        invalid = logger(status="invalid", value="na", valid_ms=-1, valid_age_ms=-1)
        self.assertEqual(parse_logger(invalid, 1)["status"], "invalid")
        with self.assertRaisesRegex(ValueError, "non-current signal status must not expose a value"):
            parse_logger(logger(status="expired", value="5.0"), 1)

    def test_diagnostic_counter_can_have_value_without_claimed_receive_time(self):
        line = ("JS_LOGGER v=1 profile=js_kr build=eng.vmadmin.20260114.090656 t_ms=1000 "
                "type=diagnostic name=binding_media_0_processed source=media.0 status=observed "
                "value=1 unit=count received_ms=-1 received_age_ms=-1 valid_ms=-1 "
                "valid_age_ms=-1 count=1")
        row = parse_logger(line, 1)
        self.assertEqual(row["source"], "media.0")
        self.assertEqual(row["value"], 1.0)
        self.assertEqual(row["received_ms"], -1)


if __name__ == "__main__":
    unittest.main()
