#!/usr/bin/env python3
"""Run synthetic host tests without Android, private logs, network or ADB."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    found = shutil.which(args.java)
    if not found:
        raise SystemExit("A compatible JDK java is required")
    java = Path(found).resolve()
    javac = java.with_name("javac.exe" if os.name == "nt" else "javac")
    hub = ROOT / "apps/hub/src/com/veloster/carplayhub"
    sources = [ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/Telemetry.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd/PdTelemetryDecoder.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/jsn/JsnTelemetryDecoder.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd/PdPowertrain.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/pd/PdGearState.java",
               *[ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js" / (name + ".java") for name in ("JsPowertrain", "JsGearState", "JsTelemetryDecoder")],
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/ShiftGuide.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view/overview/OverviewLayout.java",
               ROOT / "shared/src/com/veloster/i18n/UiText.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/HscanFrameDecoder.java",
               ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/HscanProfile.java"]
    sources += [hub / (name + ".java") for name in ("RadioTrial", "BluetoothSession", "RebootRequest", "QuickEntryPolicy")]
    tests = sorted((ROOT / "tests").glob("*Test.java"))
    sources += [ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/HudEventLog.java", ROOT / "shared/src/com/veloster/diagnostics/ErrorLog.java", ROOT / "apps/dashboard/src/main/java/com/veloster/dashboard/view/DashboardPage.java", ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsDiagState.java"]
    sources += [ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/JsLogger.java",
                ROOT / "tests/java_stubs/android/util/Log.java", ROOT / "tests/JsLoggerTest.java"]
    sources += tests
    with tempfile.TemporaryDirectory(prefix="vn-public-tests-") as temporary:
        subprocess.run([str(javac), "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], check=True)
        for test in tests:
            cls = ("com.veloster.dashboard.vehicle." if test.stem == "HscanFrameDecoderTest" else "") + test.stem
            subprocess.run([str(java), "-ea", "-cp", temporary, cls], check=True)
    print("PASS: " + str(len(tests)) + " synthetic host test programs. Vehicle operation is outside this scope.")


if __name__ == "__main__":
    main()
