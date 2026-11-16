"""Host checks for Bluetooth HAL log parsing; no Android device or logcat is used."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_quick_entry_runtime import java_tools

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/BluetoothRuntimeProbe.java"

HARNESS = r'''import com.veloster.dashboard.debugger.BluetoothRuntimeProbe;
public class BluetoothRuntimeProbeTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  BluetoothRuntimeProbe.Snapshot s=BluetoothRuntimeProbe.parse(
   "11-17 01:00:00.000 I/bt_vendor( 123): kosti!! read local name for chipID\n"+
   "11-17 01:00:00.010 I/bt_vendor( 123): kosti!! read local name result is BCM20702\n"+
   "11-17 01:00:00.020 I/bt_vendor( 123): Found patchfile: /vendor/firmware/bcm20702.hcd\n"+
   "11-17 01:00:00.030 I/bt_vendor( 123): vendor lib fwcfg completed");
  check("BCM20702".equals(s.controller)&&"bcm20702.hcd".equals(s.patch)&&s.status.contains("완료"),"completed 20702 load");
  check("11-17 01:00:00.030".equals(s.time),"completion timestamp");
  s=BluetoothRuntimeProbe.parse("11-17 01:00:00.000 I/bt_vendor( 123): Found patchfile: /vendor/firmware/bcm4335.hcd");
  check("미확인".equals(s.controller)&&s.status.contains("칩 판별 기록 없음"),"candidate alone does not identify chip");
  s=BluetoothRuntimeProbe.parse(
   "11-17 01:00:00.000 I/bt_vendor( 123): kosti!! read local name for chipID\n"+
   "11-17 01:00:00.010 I/bt_vendor( 123): kosti!! read local name result is BCM4335\n"+
   "11-17 01:00:00.020 I/bt_vendor( 123): Found patchfile: /vendor/firmware/bcm4335.hcd\n"+
   "11-17 01:00:00.030 I/bt_vendor( 123): vendor lib fwcfg completed\n"+
   "11-17 01:02:00.000 I/bt_vendor( 123): kosti!! read local name for chipID\n"+
   "11-17 01:02:00.010 I/bt_vendor( 123): kosti!! read local name result is BCM20702");
  check("BCM20702".equals(s.controller)&&s.status.contains("불완전"),"latest init cycle supersedes old successful load");
  s=BluetoothRuntimeProbe.parse(
   "11-17 01:00:00.000 I/bt_vendor( 123): kosti!! read local name for chipID\n"+
   "11-17 01:00:00.010 I/bt_vendor( 123): kosti!! read local name result is BCM4335\n"+
   "11-17 01:00:00.020 I/bt_vendor( 123): Found patchfile: /vendor/firmware/bandidate.hcd\n"+
   "11-17 01:00:00.030 I/bt_vendor( 123): vendor lib fwcfg aborted!!!");
  check(s.status.contains("실패")&&"BCM4335".equals(s.controller),"aborted init not treated as loaded");
  s=BluetoothRuntimeProbe.parse("");check("미확인".equals(s.controller),"empty log remains unknown");
  System.out.println("PASS Bluetooth HAL log parsing");
 }
}'''


class BluetoothRuntimeProbeTests(unittest.TestCase):
    def test_hal_log_records(self):
        java, javac = java_tools()
        with tempfile.TemporaryDirectory(prefix="bt-hal-test-") as tmp:
            root = Path(tmp)
            harness = root / "BluetoothRuntimeProbeTest.java"
            harness.write_text(HARNESS, encoding="utf-8")
            result = subprocess.run([javac, "-encoding", "UTF-8", "-d", str(root), str(SOURCE), str(harness)],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([java, "-cp", str(root), "BluetoothRuntimeProbeTest"],
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS Bluetooth HAL log parsing", result.stdout)


if __name__ == "__main__":
    unittest.main()
