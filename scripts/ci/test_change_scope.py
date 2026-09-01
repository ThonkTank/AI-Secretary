import unittest

from change_scope import ChangeScope, classify


class ChangeScopeTest(unittest.TestCase):
    def test_empty_and_documentation_only_changes_require_no_build(self):
        self.assertEqual(ChangeScope(False, False, False), classify([]))
        self.assertEqual(
            ChangeScope(False, False, False),
            classify(["README.md", "AGENTS.md", "docs/releasing.md"]),
        )

    def test_host_tests_require_quality_without_device_or_release(self):
        self.assertEqual(
            ChangeScope(True, False, False),
            classify(["app/src/test/java/example/ProjectionTest.java"]),
        )
        self.assertEqual(
            ChangeScope(True, False, False),
            classify(["scripts/release/test_release_tool.py"]),
        )

    def test_android_tests_and_ci_harness_require_instrumentation_without_release(self):
        for path in (
            "app/src/androidTest/java/example/DeviceTest.java",
            "app/schemas/de.example/14.json",
            "app/proguard-debug.pro",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    ChangeScope(True, True, False),
                    classify([path]),
                )

    def test_phase_zero_contract_and_archive_changes_skip_product_publish(self):
        self.assertEqual(
            ChangeScope(True, True, False),
            classify(
                [
                    "docs/architecture/training-assistant-minimal-roadmap.md",
                    "docs/architecture/adr-030-minimale-trainingsarchitektur-und-"
                    "automatisierter-abschluss.md",
                    "docs/archive/training-assistant-cleanup-2026-08/"
                    "training-assistant-cleanup-progress.md",
                    "scripts/ci/removed-obsolete-runner.sh",
                    "scripts/ci/removed-obsolete-contract.py",
                ]
            ),
        )

    def test_signed_upgrade_probe_and_fixtures_require_release(self):
        for path in (
            "app/src/androidTest/java/de/thonktank/autosecretary/"
            "UpgradePersistenceProbe.java",
            "app/src/androidTest/java/de/thonktank/autosecretary/"
            "UpgradeProbeInstrumentation.java",
            "release/upgrade-fixtures/v0.2.80.json",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    ChangeScope(True, True, True),
                    classify([path]),
                )

    def test_production_and_embedded_contract_changes_require_every_gate(self):
        for path in (
            "app/src/main/java/example/Main.java",
            "app/src/main/res/values/strings.xml",
            "core-domain/src/main/java/example/Rule.java",
            "today-core/src/main/java/example/State.java",
            "app/build.gradle.kts",
            "app/proguard-release.pro",
            ".github/workflows/verify.yml",
            "gradle/wrapper/gradle-wrapper.properties",
            "release/release.properties",
            "scripts/ci/change_scope.py",
            "scripts/ci/prepare-preview-sdk-tools.sh",
            "scripts/ci/run-upgrade-test.sh",
            "scripts/release/release_tool.py",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    ChangeScope(True, True, True),
                    classify([path]),
                )

    def test_mixed_scope_uses_the_strongest_required_gate(self):
        self.assertEqual(
            ChangeScope(True, True, True),
            classify(
                [
                    "docs/releasing.md",
                    "app/src/test/java/example/Test.java",
                    "app/src/main/java/example/Main.java",
                ]
            ),
        )

    def test_github_output_is_stable_and_lowercase(self):
        self.assertEqual(
            "quality_required=true\n"
            "instrumentation_required=false\n"
            "release_required=false",
            ChangeScope(True, False, False).github_output(),
        )


if __name__ == "__main__":
    unittest.main()
