"""Run the production DashboardView input path in the desktop platform adapter."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from dashboard_runtime import compile_runtime


class DashboardRuntimeTests(unittest.TestCase):
    def test_production_touch_handlers_and_callbacks(self):
        with tempfile.TemporaryDirectory(prefix='dashboard-runtime-test-') as folder:
            java, classes = compile_runtime(folder)
            result = subprocess.run(
                [java, '-Djava.awt.headless=true',
                 '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/main/assets'),
                 '-cp', str(classes), 'DashboardDesktop', '--check'],
                capture_output=True, text=True, timeout=30,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS actual DashboardView:', result.stdout)


if __name__ == '__main__':
    unittest.main()
