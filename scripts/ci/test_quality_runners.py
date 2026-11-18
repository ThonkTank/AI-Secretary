"""Exercise the repository's local quality entry points without launching Gradle."""
from collections import Counter
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CORE_TASKS = [":core-domain:test", ":today-core:test"]
APP_TEST = ":app:testInstrumentationUnitTest"
FULL_BUILDS = ["lintDebug", "assembleDebug", "assembleInstrumentation",
               "assembleInstrumentationAndroidTest", "assembleRelease"]


class QualityRunnerTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.repo = Path(temporary.name)
        ci = self.repo / "scripts/ci"
        ci.mkdir(parents=True)
        (self.repo / "scripts/release").mkdir()
        for name in ("all", "fast", "host", "goldens", "docs"):
            shutil.copy2(ROOT / f"scripts/ci/check-{name}.sh", ci)
        self.executable("python3", "exit 0")  # The Python suites are exercised by the real CI job.
        self.executable("gradlew", 'printf "%s\\n" "$@" >> gradle-arguments\nexit "$GRADLE_RESULT"')
        self.executable("scripts/ci/verify-instrumentation-identity.sh", "touch checked-identity")
        for path in ("app/build/outputs/apk/debug/app-debug.apk",
                     "app/build/outputs/apk/release/app-release-unsigned.apk",
                     "app/src/main/res/font/test.ttf"):
            artifact = self.repo / path
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.touch()

    def executable(self, path, script):
        target = self.repo / path
        target.write_text("#!/bin/sh\n" + script + "\n")
        target.chmod(0o755)

    def run_profile(self, profile, gradle_result=0):
        return subprocess.run([self.repo / f"scripts/ci/check-{profile}.sh"], cwd=self.repo,
                              env={**os.environ, "PATH": f"{self.repo}:{os.environ['PATH']}",
                                   "GRADLE_RESULT": str(gradle_result)},
                              capture_output=True, text=True)

    def test_selected_tasks_filters_and_identity_check(self):
        expectations = {
            "fast": CORE_TASKS + [APP_TEST, "-PqualityContractsOnly=true"],
            "host": CORE_TASKS + [APP_TEST],
            "all": CORE_TASKS + [APP_TEST] + FULL_BUILDS,
            "goldens": [APP_TEST, "--tests", "*Golden*", "--tests", "*DesignSystemTest"],
        }
        for profile, expected in expectations.items():
            with self.subTest(profile=profile):
                log = self.repo / "gradle-arguments"
                identity = self.repo / "checked-identity"
                log.unlink(missing_ok=True)
                identity.unlink(missing_ok=True)
                result = self.run_profile(profile)
                self.assertEqual(0, result.returncode, result.stderr)
                args = log.read_text().splitlines()
                # Gradle also accepts unqualified task selectors from the root project.
                args = [APP_TEST if arg == "testInstrumentationUnitTest" else arg for arg in args]
                self.assertEqual(Counter(expected), Counter(args))
                if profile == "goldens":
                    self.assertCountEqual([args[i + 1] for i, arg in enumerate(args) if arg == "--tests"],
                                          ["*Golden*", "*DesignSystemTest"])
                self.assertEqual(profile == "all", identity.exists())

    def test_gradle_failure_reaches_every_caller(self):
        for profile in ("fast", "host", "all", "goldens"):
            with self.subTest(profile=profile):
                self.assertEqual(19, self.run_profile(profile, gradle_result=19).returncode)
                self.assertFalse((self.repo / "checked-identity").exists())
