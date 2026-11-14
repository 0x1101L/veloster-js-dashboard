import contextlib
import importlib.util
import io
import json
from pathlib import Path
import types
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'diag/js_kr/carplay_adb_probe.py'
spec = importlib.util.spec_from_file_location('carplay_adb_probe', SCRIPT)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class CarPlayProbeTests(unittest.TestCase):
    def run_probe(self, responses, *args):
        calls = []
        def shell(adb, serial, *command):
            calls.append(command)
            return responses.get(command, {'status': 'unknown'})
        out = io.StringIO()
        with patch.object(probe, 'adb_shell', side_effect=shell), contextlib.redirect_stdout(out):
            status = probe.main(['--serial', 'PRIVATE_SERIAL', *args])
        return status, json.loads(out.getvalue()), calls

    def target(self):
        return {('getprop', 'ro.product.name'): {'status': 'ok', 'value': 'js_kr'},
                ('getprop', 'ro.build.version.incremental'): {'status': 'ok', 'value': probe.EXPECTED_BUILD},
                ('getprop', 'ro.build.version.sdk'): {'status': 'ok', 'value': '17'}}

    def test_wrong_build_stops_before_checks(self):
        responses = self.target()
        responses[('getprop', 'ro.build.version.incremental')]['value'] = 'wrong'
        status, result, calls = self.run_probe(responses)
        self.assertEqual(status, 2)
        self.assertEqual(result['status'], 'target_unverified')
        self.assertEqual(len(calls), 2)

    def test_selected_check_and_private_output(self):
        responses = self.target()
        responses[('pm', 'list', 'packages')] = {'status': 'ok', 'value': 'package:com.daudio.app.dipo\npackage:private.identifier'}
        status, result, calls = self.run_probe(responses, '--check', 'packages')
        self.assertEqual(status, 0)
        self.assertEqual(list(result['checks']), ['packages'])
        self.assertTrue(result['checks']['packages']['packages']['com.daudio.app.dipo'])
        self.assertNotIn('PRIVATE_SERIAL', json.dumps(result))
        self.assertNotIn('private.identifier', json.dumps(result))
        self.assertNotIn(('service', 'list'), calls)

    def test_denial_and_unrecognized_are_not_absence(self):
        responses = self.target()
        responses[('service', 'list')] = {'status': 'ok', 'value': 'Permission denied'}
        status, result, _ = self.run_probe(responses, '--check', 'services')
        self.assertEqual(status, 1)
        self.assertEqual(result['checks']['services']['status'], 'unknown')
        with patch.object(probe.subprocess, 'run', return_value=types.SimpleNamespace(returncode=0, stdout='Permission denied', stderr='')):
            self.assertEqual(probe.adb_shell('adb', 'x', 'service', 'list')['status'], 'denied')

    def test_raw_is_explicit_and_missing_is_not_success(self):
        responses = self.target()
        responses[('pm', 'list', 'packages')] = {'status': 'ok', 'value': 'package:com.daudio.app.dipo'}
        responses[('service', 'list')] = {'status': 'ok', 'value': '0 com.hkmc.micom.cluster: [x]'}
        status, result, calls = self.run_probe(responses)
        self.assertEqual(status, 0)
        self.assertEqual(list(result['checks']), ['packages', 'services'])
        self.assertFalse(any('run-as' in call for call in calls))
        status, result, _ = self.run_probe(responses, '--check', 'raw-current')
        self.assertEqual(status, 1)
        self.assertEqual(result['checks']['raw-current']['status'], 'unknown')

    def test_raw_records_return_only_on_explicit_request(self):
        responses = self.target()
        command = ('run-as', 'com.veloster.dashboard', 'cat', 'files/diagnostics/carplay-raw.log')
        record = 'CARPLAY_RAW v=1 elapsed_ms=1 source=iap2 type=0x5202 original_length=1 stored_length=1 state=complete hex=ff'
        responses[command] = {'status': 'ok', 'value': record}
        status, result, _ = self.run_probe(responses, '--check', 'raw-current')
        self.assertEqual(status, 0)
        self.assertEqual(result['checks']['raw-current']['records'], [record])

    def test_service_filter_exposes_only_allowlist(self):
        with patch.object(probe, 'adb_shell', return_value={'status': 'ok', 'value': '0 com.hkmc.micom.cluster: [x]\n1 private.secret: [y]'}):
            result = probe.service_check('adb', 'x')
        self.assertTrue(result['services']['com.hkmc.micom.cluster'])
        self.assertNotIn('private.secret', json.dumps(result))


if __name__ == '__main__':
    unittest.main()
