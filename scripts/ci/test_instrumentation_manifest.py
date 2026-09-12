import unittest

from instrumentation_manifest import DIAGNOSTIC_RUNNERS, NAMESPACE, required_runners, validate


def manifest(pairs):
    return "\n".join('    E: instrumentation (line=9)\n'
                     f'      A: android:name(0x01010003)="{name}"\n'
                     f'      A: android:targetPackage(0x01010021)="{target}"'
                     for name, target in pairs)


class InstrumentationManifestTest(unittest.TestCase):
    def test_regular_and_signed_components_are_paired_with_their_actual_target(self):
        for primary, target in [('androidx.test.runner.AndroidJUnitRunner', NAMESPACE + '.test'),
                                (NAMESPACE + '.UpgradeProbeInstrumentation', NAMESPACE)]:
            pairs = [(name, target) for name in sorted(required_runners(primary))]
            self.assertEqual(dict(pairs), validate(manifest(pairs), target, primary))

    def test_signed_helper_cannot_expose_the_isolated_normal_or_fixture_runner(self):
        primary = NAMESPACE + '.UpgradeProbeInstrumentation'
        pairs = [(name, NAMESPACE) for name in required_runners(primary)]
        for name in ['DiagnosticFixtureInstrumentation', 'NormalDiagnosticRecoveryInstrumentation']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                validate(manifest(pairs + [(NAMESPACE + '.' + name, NAMESPACE)]), NAMESPACE, primary)

    def test_missing_overwritten_diagnostic_runner_is_rejected(self):
        primary, target = 'androidx.test.runner.AndroidJUnitRunner', NAMESPACE + '.test'
        pairs = [(name, target) for name in DIAGNOSTIC_RUNNERS | {primary}
                 if name != NAMESPACE + '.DiagnosticProbeInstrumentation']
        with self.assertRaises(ValueError):
            validate(manifest(pairs), target, primary)

    def test_one_wrong_target_cannot_hide_behind_other_correct_components(self):
        primary, target = 'androidx.test.runner.AndroidJUnitRunner', NAMESPACE + '.test'
        pairs = [(name, target) for name in sorted(DIAGNOSTIC_RUNNERS | {primary})]
        pairs[1] = (pairs[1][0], NAMESPACE)
        with self.assertRaises(ValueError):
            validate(manifest(pairs), target, primary)

    def test_duplicate_component_and_missing_attribute_fail(self):
        primary, target = 'androidx.test.runner.AndroidJUnitRunner', NAMESPACE + '.test'
        pairs = [(name, target) for name in sorted(DIAGNOSTIC_RUNNERS | {primary})]
        for text in [manifest(pairs + [pairs[0]]), manifest(pairs) + '\n    E: instrumentation\n      A: android:name(0x01010003)="bad"']:
            with self.assertRaises(ValueError):
                validate(text, target, primary)


if __name__ == '__main__':
    unittest.main()
