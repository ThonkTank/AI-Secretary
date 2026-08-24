import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "ci" / "run-instrumentation.sh"


class InstrumentationRunnerTest(unittest.TestCase):
    def test_failure_exit_code_is_preserved_and_diagnostics_are_collected(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            gradle = self._executable(temporary / "gradle", "#!/usr/bin/env bash\nexit 23\n")
            adb_log = temporary / "adb-calls.txt"
            self._executable(
                temporary / "adb",
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    printf '%s\n' "$*" >> "$ADB_CALL_LOG"
                    if [ "$1" = pull ]; then
                      printf '<hierarchy />\n' > "$3"
                    else
                      printf 'captured %s\n' "$*"
                    fi
                    """
                ),
            )
            report_root = temporary / "reports"
            environment = os.environ.copy()
            environment.update(
                {
                    "ADB_CALL_LOG": str(adb_log),
                    "INSTRUMENTATION_API_LEVEL": "35",
                    "INSTRUMENTATION_ATTEMPT": "4",
                    "INSTRUMENTATION_GRADLE_EXECUTABLE": str(gradle),
                    "INSTRUMENTATION_REPORT_ROOT": str(report_root),
                    "PATH": str(temporary) + os.pathsep + environment["PATH"],
                }
            )

            result = subprocess.run([str(RUNNER)], cwd=ROOT, env=environment, check=False)

            self.assertEqual(23, result.returncode)
            report = report_root / "api-35" / "attempt-4"
            self.assertIn("gradle_exit_code=23", (report / "run-context.txt").read_text())
            self.assertTrue((report / "screenshot.png").is_file())
            self.assertTrue((report / "ui-hierarchy.xml").is_file())
            self.assertTrue((report / "logcat.txt").is_file())
            calls = adb_log.read_text()
            for command in (
                "exec-out screencap -p",
                "shell uiautomator dump",
                "logcat -d -v threadtime",
                "shell getevent -lp",
                "shell dumpsys input",
                "shell dumpsys display",
            ):
                with self.subTest(command=command):
                    self.assertIn(command, calls)

    def test_success_does_not_create_failure_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            gradle = self._executable(temporary / "gradle", "#!/usr/bin/env bash\nexit 0\n")
            report_root = temporary / "reports"
            environment = os.environ.copy()
            environment.update(
                {
                    "INSTRUMENTATION_GRADLE_EXECUTABLE": str(gradle),
                    "INSTRUMENTATION_REPORT_ROOT": str(report_root),
                }
            )

            result = subprocess.run([str(RUNNER)], cwd=ROOT, env=environment, check=False)

            self.assertEqual(0, result.returncode)
            self.assertFalse(report_root.exists())

    def test_requested_animation_scale_is_set_and_verified_before_gradle(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            gradle_marker = temporary / "gradle-ran.txt"
            gradle = self._executable(
                temporary / "gradle",
                "#!/usr/bin/env bash\nprintf 'yes\\n' > \"$GRADLE_MARKER\"\n",
            )
            adb_log = temporary / "adb-calls.txt"
            self._executable(
                temporary / "adb",
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    printf '%s\n' "$*" >> "$ADB_CALL_LOG"
                    if [ "$1 $2 $3" = "shell settings get" ]; then
                      printf '%s\n' "$EXPECTED_SCALE"
                    fi
                    """
                ),
            )
            environment = os.environ.copy()
            environment.update(
                {
                    "ADB_CALL_LOG": str(adb_log),
                    "EXPECTED_SCALE": "1.0",
                    "GRADLE_MARKER": str(gradle_marker),
                    "INSTRUMENTATION_ANIMATION_SCALE": "1.0",
                    "INSTRUMENTATION_GRADLE_EXECUTABLE": str(gradle),
                    "PATH": str(temporary) + os.pathsep + environment["PATH"],
                }
            )

            result = subprocess.run([str(RUNNER)], cwd=ROOT, env=environment, check=False)

            self.assertEqual(0, result.returncode)
            self.assertTrue(gradle_marker.is_file())
            calls = adb_log.read_text()
            for setting in (
                "window_animation_scale",
                "transition_animation_scale",
                "animator_duration_scale",
            ):
                with self.subTest(setting=setting):
                    self.assertIn(f"shell settings put global {setting} 1.0", calls)
                    self.assertIn(f"shell settings get global {setting}", calls)

    def test_animation_scale_mismatch_fails_before_gradle_and_is_diagnosed(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            gradle_marker = temporary / "gradle-ran.txt"
            gradle = self._executable(
                temporary / "gradle",
                "#!/usr/bin/env bash\nprintf 'yes\\n' > \"$GRADLE_MARKER\"\n",
            )
            self._executable(
                temporary / "adb",
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    if [ "$1 $2 $3" = "shell settings get" ]; then
                      printf '0.0\n'
                    fi
                    """
                ),
            )
            report_root = temporary / "reports"
            environment = os.environ.copy()
            environment.update(
                {
                    "GRADLE_MARKER": str(gradle_marker),
                    "INSTRUMENTATION_ANIMATION_SCALE": "1.0",
                    "INSTRUMENTATION_API_LEVEL": "37",
                    "INSTRUMENTATION_GRADLE_EXECUTABLE": str(gradle),
                    "INSTRUMENTATION_REPORT_ROOT": str(report_root),
                    "PATH": str(temporary) + os.pathsep + environment["PATH"],
                }
            )

            result = subprocess.run([str(RUNNER)], cwd=ROOT, env=environment, check=False)

            self.assertEqual(42, result.returncode)
            self.assertFalse(gradle_marker.exists())
            report = report_root / "api-37" / "attempt-1"
            self.assertIn("animation_scale=1.0", (report / "run-context.txt").read_text())
            self.assertIn(
                "animator_duration_scale=0.0",
                (report / "animation-scales.txt").read_text(),
            )

    @staticmethod
    def _executable(path: pathlib.Path, contents: str) -> pathlib.Path:
        path.write_text(contents, encoding="utf-8")
        path.chmod(0o755)
        return path


if __name__ == "__main__":
    unittest.main()
