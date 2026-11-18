"""Run the production DashboardView input path in the desktop platform adapter."""
from pathlib import Path
from io import StringIO
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from dashboard_runtime import compile_runtime
import dashboard_runtime


class DashboardRuntimeTests(unittest.TestCase):
    def test_refresh_recompiles_and_restarts_preview_process(self):
        class FakeProcess:
            def __init__(self, output):
                self.stdout = StringIO(''.join(output))
                self.stdin = type('Input', (), {'write': lambda *_: None, 'flush': lambda *_: None,
                                                'close': lambda *_: None})()
                self.terminated = False

            def poll(self):
                return 0 if self.terminated else None

            def terminate(self):
                self.terminated = True

            def wait(self, timeout=None):
                return 0

        processes = [FakeProcess(['DASHBOARD_PREVIEW_RELOAD\n']), FakeProcess([])]
        compile_calls = []
        with patch.object(dashboard_runtime, 'compile_runtime',
                          side_effect=lambda folder: (compile_calls.append(Path(folder)) or ('java', Path(folder)))):
            with patch.object(dashboard_runtime.subprocess, 'Popen', side_effect=processes) as popen:
                dashboard_runtime.run()

        self.assertEqual(len(compile_calls), 2)
        self.assertEqual(len(popen.call_args_list), 2)
        self.assertEqual(processes[0].terminated, True)
        self.assertEqual(popen.call_args_list[0].args[0][popen.call_args_list[0].args[0].index('-cp') + 1],
                         str(compile_calls[0]))
        self.assertEqual(popen.call_args_list[1].args[0][popen.call_args_list[1].args[0].index('-cp') + 1],
                         str(compile_calls[1]))

    def test_production_touch_handlers_and_callbacks(self):
        with tempfile.TemporaryDirectory(prefix='dashboard-runtime-test-') as folder:
            java, classes = compile_runtime(folder)
            preferences = Path(folder) / 'preferences'
            result = subprocess.run(
                [java, '-Djava.awt.headless=true',
                 '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/0x1101L/assets'),
                 '-cp', str(classes), 'DashboardDesktop', '--check'],
                capture_output=True, text=True, timeout=30,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('PASS actual DashboardView:', result.stdout)
            for mode, expected in (
                ('--prefs-write', 'PASS preferences written through dashboard editors'),
                ('--prefs-read', 'PASS preferences restored in a fresh process'),
            ):
                result = subprocess.run(
                    [java, '-Djava.awt.headless=true',
                     '-Ddashboard.preferences.dir=' + str(preferences),
                     '-Ddashboard.assets=' + str(ROOT / 'apps/dashboard/src/0x1101L/assets'),
                     '-cp', str(classes), 'DashboardDesktop', mode],
                    capture_output=True, text=True, timeout=30,
                )
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn(expected, result.stdout)


if __name__ == '__main__':
    unittest.main()
