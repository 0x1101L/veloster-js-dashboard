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
        with patch.object(probe, 'adb_shell', side_effect=shell), \
                patch.object(probe, 'adb_device_state', return_value={'status': 'ok', 'value': 'device'}), \
                contextlib.redirect_stdout(out):
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
        self.assertIn('inventory or allowlisted prior HUD_EVENT', result['limitations'])
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

    def test_sequence_stops_after_failed_package_check(self):
        responses = self.target()
        responses[('pm', 'list', 'packages')] = {'status': 'denied', 'detail': 'permission denied'}
        status, result, calls = self.run_probe(responses, '--check', 'packages', '--check', 'services')
        self.assertEqual(status, 1)
        self.assertEqual(list(result['checks']), ['packages'])
        self.assertEqual(result['skipped_checks'], ['services'])
        self.assertNotIn(('service', 'list'), calls)

    def test_default_inventory_never_reads_raw_logs(self):
        responses = self.target()
        responses[('pm', 'list', 'packages')] = {'status': 'ok', 'value': 'package:com.daudio.app.dipo'}
        responses[('service', 'list')] = {'status': 'ok', 'value': '0 com.hkmc.micom.cluster: [x]'}
        status, result, calls = self.run_probe(responses)
        self.assertEqual(status, 0)
        self.assertEqual(list(result['checks']), ['packages', 'services'])
        self.assertFalse(any('run-as' in call for call in calls))

    def test_raw_log_read_is_not_a_registered_check(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            probe.main(['--serial', 'PRIVATE_SERIAL', '--check', 'raw-current'])
        self.assertEqual(raised.exception.code, 2)

    def test_offline_adb_device_stops_before_property_checks(self):
        output = io.StringIO()
        with patch.object(probe, 'adb_device_state', return_value={'status': 'error', 'detail': 'offline'}), \
                patch.object(probe, 'adb_shell') as shell, contextlib.redirect_stdout(output):
            status = probe.main(['--serial', 'PRIVATE_SERIAL'])
        self.assertEqual(status, 2)
        self.assertEqual(json.loads(output.getvalue())['status'], 'target_unverified')
        shell.assert_not_called()

    def test_service_filter_exposes_only_allowlist(self):
        with patch.object(probe, 'adb_shell', return_value={'status': 'ok', 'value': '0 com.hkmc.micom.cluster: [x]\n1 private.secret: [y]'}):
            result = probe.service_check('adb', 'x')
        self.assertTrue(result['services']['com.hkmc.micom.cluster'])
        self.assertNotIn('private.secret', json.dumps(result))

    def test_event_log_read_returns_only_allowlisted_non_raw_fields(self):
        data = ('HUD_EVENT v=1 elapsed_ms=123 event=carplay.media.mode value=active\n'
                'HUD_EVENT v=1 elapsed_ms=124 event=binding.media.3 value=received_1024\n'
                'HUD_EVENT v=1 elapsed_ms=125 event=navigation.route_state value=2\n'
                'HUD_EVENT v=1 elapsed_ms=126 event=carplay.media.title value=private-title\n'
                'CARPLAY_RAW v=1 payload=secret\n')
        with patch.object(probe, 'adb_shell', return_value={'status': 'ok', 'value': data}) as shell:
            result = probe.event_log_check('adb', 'x', 'hud-events.log')
        shell.assert_called_once_with('adb', 'x', 'run-as', 'com.veloster.dashboard', 'cat',
                                      'files/diagnostics/hud-events.log')
        self.assertEqual(result['status'], 'ok')
        self.assertEqual([r['event'] for r in result['records']],
                         ['carplay.media.mode', 'binding.media.3', 'navigation.route_state'])
        self.assertNotIn('private-title', json.dumps(result))
        self.assertNotIn('secret', json.dumps(result))

    def test_event_mode_output_describes_event_log_scope(self):
        responses = self.target()
        responses[('run-as', 'com.veloster.dashboard', 'cat', 'files/diagnostics/hud-events.log')] = {
            'status': 'ok', 'value': 'HUD_EVENT v=1 elapsed_ms=4 event=carplay.media.mode value=active'}
        status, result, _ = self.run_probe(responses, '--check', 'events-current')
        self.assertEqual(status, 0)
        self.assertIn('allowlisted prior HUD_EVENT', result['limitations'])
        self.assertNotIn('Inventory only', result['limitations'])

    def test_empty_event_log_is_unknown_and_never_tbt_absence(self):
        with patch.object(probe, 'adb_shell', return_value={'status': 'ok', 'value': ''}):
            result = probe.event_log_check('adb', 'x', 'hud-events.log')
        self.assertEqual(result['status'], 'unknown')
        self.assertIn('not evidence of TBT absence', result['detail'])

    def test_event_file_failure_does_not_skip_previous_file(self):
        responses = self.target()
        responses[('run-as', 'com.veloster.dashboard', 'cat', 'files/diagnostics/hud-events.log')] = {
            'status': 'unknown', 'detail': 'missing'}
        responses[('run-as', 'com.veloster.dashboard', 'cat', 'files/diagnostics/hud-events.previous.log')] = {
            'status': 'ok', 'value': 'HUD_EVENT v=1 elapsed_ms=4 event=carplay.media.mode value=active'}
        status, result, calls = self.run_probe(responses, '--check', 'events-current', '--check', 'events-previous')
        self.assertEqual(status, 1)
        self.assertEqual(list(result['checks']), ['events-current', 'events-previous'])
        self.assertEqual(result['checks']['events-previous']['records'][0]['event'], 'carplay.media.mode')
        self.assertEqual(len([c for c in calls if c[0] == 'run-as']), 2)


if __name__ == '__main__':
    unittest.main()
