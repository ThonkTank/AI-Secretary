import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "ci" / "run-upgrade-test.sh"


class UpgradeRunnerTest(unittest.TestCase):
    def run_runner(self, package_present=False, probe_crash=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        log = root / "adb.log"
        for name in ("previous.apk", "candidate.apk", "test.apk"):
            (root / name).write_bytes(b"apk")
        adb = root / "adb"
        adb.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env bash
                set -euo pipefail
                printf '%s\\n' "$*" >> "$ADB_LOG"
                case "$*" in
                  "install "*) echo Success ;;
                  "uninstall de.example.autosecretary") echo Success ;;
                  "shell pm list packages de.example.autosecretary")
                    if [ "${ADB_PACKAGE_PRESENT:-false}" = true ]; then
                      echo package:de.example.autosecretary
                    fi
                    ;;
                  "shell pm path "*) exit 1 ;;
                  "shell am start "*) echo 'Status: ok' ;;
                  "shell dumpsys package de.example.autosecretary") echo '  versionCode=123 minSdk=26 targetSdk=35' ;;
                  "shell am instrument "*currentVersionStartsAndReadsPreviousData*)
                    if [ "${ADB_PROBE_CRASH:-false}" = true ]; then
                      echo 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
                      exit 1
                    fi
                    echo 'OK (1 test)'
                    ;;
                  "shell am instrument "*) echo 'OK (1 test)' ;;
                  "logcat -d -v threadtime") echo 'FATAL EXCEPTION: InstrumentationThread' ;;
                  *) echo OK ;;
                esac
                """
            ),
            encoding="utf-8",
        )
        adb.chmod(0o755)
        environment = os.environ.copy()
        environment["PATH"] = f"{root}:{environment['PATH']}"
        environment["ADB_LOG"] = str(log)
        environment["ADB_PACKAGE_PRESENT"] = str(package_present).lower()
        environment["ADB_PROBE_CRASH"] = str(probe_crash).lower()
        result = subprocess.run(
            [
                str(RUNNER),
                str(root / "previous.apk"),
                str(root / "candidate.apk"),
                str(root / "test.apk"),
                "de.example.autosecretary",
                "123",
            ],
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )
        return root, result, log.read_text(encoding="utf-8").splitlines()

    def test_clean_candidate_install_precedes_independent_upgrade_path(self):
        root, result, calls = self.run_runner()

        self.assertEqual(0, result.returncode, result.stderr)
        fresh_candidate = calls.index(f"install {root / 'candidate.apk'}")
        uninstall = calls.index("uninstall de.example.autosecretary")
        previous = calls.index(f"install {root / 'previous.apk'}")
        test_apk = calls.index(f"install {root / 'test.apk'}")
        upgraded_candidate = calls.index(f"install -r {root / 'candidate.apk'}")
        self.assertLess(fresh_candidate, uninstall)
        self.assertLess(uninstall, previous)
        self.assertLess(previous, test_apk)
        self.assertLess(test_apk, upgraded_candidate)
        self.assertEqual(
            2,
            sum(call.startswith("shell am instrument ") for call in calls),
        )
        self.assertEqual(
            2,
            sum(call.startswith("shell am start ") for call in calls),
        )
        self.assertEqual(
            2,
            sum(
                call == "shell pm list packages de.example.autosecretary"
                for call in calls
            ),
        )

    def test_existing_package_fails_before_candidate_install(self):
        root, result, calls = self.run_runner(package_present=True)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("Package is still installed", result.stderr)
        self.assertNotIn(f"install {root / 'candidate.apk'}", calls)

    def test_candidate_probe_crash_prints_device_diagnostics(self):
        _, result, calls = self.run_runner(probe_crash=True)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("Upgrade probe 'verify' failed", result.stderr)
        self.assertIn("FATAL EXCEPTION: InstrumentationThread", result.stderr)
        self.assertIn("logcat -d -v threadtime", calls)


if __name__ == "__main__":
    unittest.main()
