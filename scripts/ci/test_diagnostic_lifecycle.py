import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('diagnostic_lifecycle', Path(__file__).with_name('run-diagnostic-lifecycle.py'))
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)


class DiagnosticLifecycleTest(unittest.TestCase):
    def test_activity_requires_real_creation_and_finishing_destruction_in_same_process(self):
        proof = {'pid': 123, 'activity': lifecycle.NAMESPACE + '.MainActivity',
                 'created': True, 'destroyed': True, 'finishing': True, 'diagnosing': True}
        lifecycle.validate_activity(proof, 123, 'MainActivity')
        for key, value in [('pid', 124), ('activity', 'other.Activity'), ('created', False),
                           ('destroyed', False), ('finishing', False), ('diagnosing', False)]:
            with self.subTest(key=key), self.assertRaises(AssertionError):
                lifecycle.validate_activity({**proof, key: value}, 123, 'MainActivity')

    def test_interval_witness_cannot_come_from_another_process_or_previous_run(self):
        output = 'INSTRUMENTATION_STATUS: diagnosticVerified=protocol=1; pid=123; token=abc\n'
        self.assertTrue(lifecycle.verified_interval(output, 123, 'abc'))
        self.assertFalse(lifecycle.verified_interval(output, 124, 'abc'))
        self.assertFalse(lifecycle.verified_interval(output, 123, 'old'))
        self.assertFalse(lifecycle.verified_interval('OK (1 diagnostic probe)', 123, 'abc'))

    def test_physical_device_is_rejected_before_root_install_or_fixture_work(self):
        with patch.object(lifecycle.Device, 'shell', return_value='0\n') as shell, \
                patch.object(lifecycle.subprocess, 'run') as transport:
            with self.assertRaisesRegex(AssertionError, 'physical device'):
                lifecycle.run('physical', Path('/unused-report'))
            shell.assert_called_once_with('getprop', 'ro.kernel.qemu')
            transport.assert_not_called()


if __name__ == '__main__':
    unittest.main()
