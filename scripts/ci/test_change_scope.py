import tempfile
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

from change_scope import (Change, ChangeScope, POLICY_VERSION, TODAY_PRODUCTION, TODAY_TESTS,
                          classify, classify_changes, git_scope, parse_name_status, required_jobs)

COORDINATOR = next(p for p in TODAY_PRODUCTION if p.endswith('/TodayCoordinator.java'))
DISPATCHER = next(p for p in TODAY_PRODUCTION if p.endswith('/TodayCommandDispatcher.java'))


class ChangeScopeTest(unittest.TestCase):
    def test_empty_and_documentation_changes_have_no_android_or_release(self):
        for paths in ([], ['README.md', 'AGENTS.md', 'docs/releasing.md']):
            scope = classify(paths)
            self.assertEqual('docs', scope.profile)
            self.assertFalse(scope.quality_required)
            self.assertFalse(scope.instrumentation_required)
            self.assertFalse(scope.release_required)

    def test_host_tests_include_new_tests_without_device_or_release(self):
        for path in ('app/src/test/java/example/ProjectionTest.java', 'scripts/release/test_release_tool.py'):
            scope = classify_changes([Change('A', path)])
            self.assertEqual('host', scope.profile)
            self.assertTrue(scope.quality_required)
            self.assertFalse(scope.instrumentation_required)
            self.assertFalse(scope.release_required)

    def test_only_audited_today_files_and_direct_tests_use_today_profile(self):
        scope = classify([COORDINATOR, DISPATCHER, *TODAY_TESTS, 'docs/releasing.md'])
        self.assertEqual('today', scope.profile)
        self.assertTrue(scope.build_required)
        self.assertTrue(scope.release_required)
        self.assertFalse(scope.goldens_required)
        self.assertFalse(scope.normal_instrumentation_required)
        self.assertFalse(scope.historical_upgrades_required)
        self.assertEqual([35], [r['api-level'] for r in scope.animation_matrix['include']])

    def test_today_mixed_with_an_unrelated_test_is_full(self):
        self.assertEqual('full', classify([COORDINATOR, 'app/src/test/java/example/OtherTest.java']).profile)

    def test_unknown_and_platform_storage_build_policy_changes_are_full(self):
        paths = [
            'app/src/main/java/example/Main.java', 'app/src/main/AndroidManifest.xml',
            'app/src/main/java/example/WidgetReceiver.java', 'app/src/main/java/example/DatabaseMigrations.java',
            'core-domain/src/main/java/example/Rule.java', 'today-core/src/main/java/example/State.java',
            'app/build.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties',
            'scripts/ci/change_scope.py', 'scripts/ci/new-policy.py', '.github/workflows/verify.yml',
            'scripts/ci/reuse_pr_verification.py', 'scripts/ci/run-upgrade-test.sh',
            'scripts/release/release_tool.py', 'unknown-input.conf',
        ]
        for path in paths:
            with self.subTest(path=path):
                for changes in ([path], [COORDINATOR, path]):
                    scope = classify(changes)
                    self.assertEqual('full', scope.profile)
                    self.assertTrue(scope.release_required)
                    self.assertTrue(scope.historical_upgrades_required)
                    self.assertTrue(scope.normal_instrumentation_required)
                    self.assertEqual(3, len(scope.animation_matrix['include']))

    def test_test_apks_harnesses_and_exports_still_do_not_publish(self):
        for path in ('app/src/androidTest/java/example/DeviceTest.java', 'app/src/debug/java/Host.java',
                     'app/schemas/de.example/27.json', 'app/proguard-debug.pro'):
            scope = classify([path])
            self.assertEqual('full', scope.profile)
            self.assertTrue(scope.instrumentation_required)
            self.assertFalse(scope.release_required)

    def test_signed_upgrade_probe_and_corpus_are_full_release_inputs(self):
        for path in ('app/src/androidTest/java/de/thonktank/autosecretary/UpgradeProbeInstrumentation.java',
                     'app/src/androidTest/java/de/thonktank/autosecretary/UpgradePersistenceProbe.java',
                     'app/src/androidTest/java/de/thonktank/autosecretary/EarlyDiagnosticInstrumentation.java',
                     'app/src/androidTest/java/de/thonktank/autosecretary/DiagnosticProbeInstrumentation.java',
                     'app/src/androidTest/AndroidManifest.xml',
                     'app/src/androidTest/AndroidManifest-upgrade.xml',
                     'release/upgrade-fixtures/schema-20-organic-flow.json',
                     'scripts/release/upgrade_fixture_tool.py'):
            scope = classify([path])
            self.assertEqual('full', scope.profile)
            self.assertTrue(scope.release_required)

    def test_new_production_file_rename_deletion_and_type_change_never_take_today_shortcut(self):
        for change in [Change('A', COORDINATOR), Change('D', COORDINATOR), Change('T', COORDINATOR),
                       Change('R100', COORDINATOR, 'app/src/main/java/Old.java'),
                       Change('R100', 'docs/disguised.md', COORDINATOR),
                       Change('C100', DISPATCHER, COORDINATOR)]:
            scope = classify_changes([change])
            self.assertEqual('full', scope.profile)
            self.assertTrue(scope.release_required)

    def test_invalid_paths_and_unknown_status_fail_closed(self):
        for change in [Change('X', 'README.md'), Change('MALFORMED', 'README.md'), Change('R200', 'README.md', 'old.md'), Change('', COORDINATOR),
                       Change('M', COORDINATOR+'\n'), Change('M', '../README.md'),
                       Change('M', '/README.md'), Change('M', COORDINATOR.replace('/', '\\'))]:
            scope = classify_changes([change])
            self.assertEqual('full', scope.profile)
            self.assertTrue(scope.release_required)

    def test_git_parser_retains_both_rename_paths(self):
        raw = f'M\0{COORDINATOR}\0R100\0app/src/main/Old.java\0docs/new.md\0'
        self.assertEqual((Change('M', COORDINATOR), Change('R100', 'docs/new.md', 'app/src/main/Old.java')),
                         parse_name_status(raw))
        for malformed in ('M', 'M\0', 'R100\0old\0'):
            with self.assertRaises(ValueError):
                parse_name_status(malformed)

    def test_missing_git_basis_is_full_not_an_empty_diff(self):
        with patch('change_scope.subprocess.check_output', side_effect=subprocess.CalledProcessError(128, 'git')):
            scope = git_scope('missing-base')
        self.assertEqual('full', scope.profile)
        self.assertTrue(scope.release_required)
        self.assertIn('diff_evidence_unavailable', scope.reasons)

    def test_local_untracked_production_input_is_included(self):
        with patch('change_scope.subprocess.check_output', side_effect=['a' * 40, f'M\0{COORDINATOR}\0',
                                                                      'app/src/main/java/New.java\0']):
            scope = git_scope('main', working_tree=True)
        self.assertEqual('full', scope.profile)
        self.assertTrue(scope.release_required)

    def test_real_git_status_and_untracked_paths_are_used_without_option_injection(self):
        with tempfile.TemporaryDirectory() as folder:
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=folder, text=True, stderr=subprocess.PIPE)
            git('init', '-q')
            path = Path(folder, COORDINATOR)
            path.parent.mkdir(parents=True)
            path.write_text('baseline')
            git('add', '.')
            git('-c', 'user.name=Scope test', '-c', 'user.email=scope@example.invalid',
                'commit', '-qm', 'baseline')
            base = git('rev-parse', 'HEAD').strip()
            path.write_text('forwarding change')
            self.assertEqual('today', git_scope(base, working_tree=True, cwd=folder).profile)
            Path(folder, 'unknown-build-input').write_text('new')
            self.assertEqual('full', git_scope(base, working_tree=True, cwd=folder).profile)
            self.assertEqual('full', git_scope('--output=unexpected', cwd=folder).profile)
            self.assertFalse(Path(folder, 'unexpected').exists())
            Path(folder, 'unknown-build-input').unlink()
            path.unlink()
            self.assertEqual('full', git_scope(base, working_tree=True, cwd=folder).profile)

    def test_outputs_and_required_jobs_bind_policy_and_selected_individual_jobs(self):
        scope = classify([COORDINATOR])
        output = dict(line.split('=', 1) for line in scope.github_output().splitlines())
        self.assertEqual('today', output['verification_profile'])
        self.assertEqual(POLICY_VERSION, output['verification_policy'])
        self.assertEqual('true', output['build_required'])
        self.assertEqual('false', output['historical_upgrades_required'])
        jobs = required_jobs('today')
        self.assertIn('verification-policy (1, today)', jobs)
        self.assertIn('quality-contracts', jobs)
        self.assertIn('quality-build', jobs)
        self.assertIn('animation-instrumentation (35, google_apis, x86_64, stable)', jobs)
        self.assertNotIn('quality-goldens', jobs)
        self.assertEqual(6, len([j for j in required_jobs('full') if '-instrumentation (' in j or j.startswith('instrumentation (')]))
        with self.assertRaises(ValueError):
            required_jobs('unknown')
        with self.assertRaises(ValueError):
            ChangeScope('docs', True)


if __name__ == '__main__':
    unittest.main()
