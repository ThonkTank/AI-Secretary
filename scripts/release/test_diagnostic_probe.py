import unittest
import subprocess
from unittest.mock import patch

from diagnostic_probe import diagnose, interpret


class DiagnosticProbeTest(unittest.TestCase):
    def test_explicit_unsupported_is_not_a_successful_diagnosis(self):
        output = ('INSTRUMENTATION_RESULT: diagnosticProtocol=1\n'
                  'INSTRUMENTATION_RESULT: diagnosticUnsupported=No early contract\n'
                  'INSTRUMENTATION_RESULT: stream=\nUNSUPPORTED_DIAGNOSTIC_PROTOCOL\n')
        self.assertEqual('unsupported', interpret(output)['status'])
        with self.assertRaises(ValueError):
            interpret(output + 'OK (1 diagnostic probe)')

    def test_generic_crash_missing_report_and_wrong_protocol_fail(self):
        good = ('INSTRUMENTATION_RESULT: diagnosticProtocol=1\n'
                'INSTRUMENTATION_RESULT: diagnosis=schema=27; foreignKeys={}\n'
                'INSTRUMENTATION_RESULT: stream=\nOK (1 diagnostic probe)\n')
        self.assertEqual('schema=27; foreignKeys={}', interpret(good)['diagnosis'])
        for bad in [good.replace('diagnosticProtocol=1', 'diagnosticProtocol=2'),
                    good.replace('INSTRUMENTATION_RESULT: diagnosis=', 'missing='),
                    good + 'Process crashed', 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.']:
            with self.subTest(output=bad), self.assertRaises(ValueError):
                interpret(bad)

    def test_successor_normal_process_does_not_mean_diagnosis_is_still_running(self):
        namespace = 'de.thonktank.autosecretary'
        output = ('INSTRUMENTATION_RESULT: diagnosticProtocol=1\n'
                  'INSTRUMENTATION_RESULT: diagnosticPid=123\n'
                  'INSTRUMENTATION_RESULT: diagnosis=schema=27; foreignKeys={}\n'
                  'OK (1 diagnostic probe)\n')
        replies = [f'instrumentation:{namespace}.test/{namespace}.DiagnosticProbeInstrumentation (target={namespace})\n',
                   'versionCode=1017401', output, '123 456\n', '456\n']
        with patch('diagnostic_probe.subprocess.run', side_effect=[
                subprocess.CompletedProcess([], 0, stdout=text) for text in replies]) as transport, \
                patch('diagnostic_probe.time.sleep'):
            result = diagnose('isolated', namespace)
        self.assertEqual(123, result['diagnosticPid'])
        self.assertTrue(result['processExited'])
        self.assertTrue(result['normalProcessRunning'])
        self.assertEqual(5, transport.call_count)

    def test_unknown_package_is_rejected_before_device_access(self):
        with patch('diagnostic_probe.subprocess.run') as transport:
            with self.assertRaises(ValueError):
                diagnose('device', 'some.other.package')
            transport.assert_not_called()


if __name__ == '__main__':
    unittest.main()
