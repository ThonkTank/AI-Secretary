import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "ci" / "run-upgrade-test.sh"


class UpgradeRunnerTest(unittest.TestCase):
    def run_runner(self, package_present=False, probe_crash=False, diagnostics=None):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = pathlib.Path(temporary.name)
        log = root / "adb.log"
        for name in ("source.apk", "candidate.apk", "test.apk"):
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
                  "shell am instrument "*"-e upgradePhase verify"*)
                    if [ "${ADB_PROBE_CRASH:-false}" = true ]; then
                      echo 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.'
                      exit 1
                    fi
                    echo 'OK (1 probe)'
                    ;;
                  "shell am instrument "*) echo 'OK (1 probe)' ;;
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
        if diagnostics is not None:
            python = root / "python3"
            python.write_text("#!/usr/bin/env bash\n"
                'if [ ! -f "$DIAGNOSTIC_SOURCE_DONE" ]; then\n'
                '  touch "$DIAGNOSTIC_SOURCE_DONE"\n'
                '  echo "diagnostic source" >> "$ADB_LOG"\n'
                '  exit "$DIAGNOSTIC_SOURCE_STATUS"\n'
                'fi\n'
                'echo "diagnostic candidate" >> "$ADB_LOG"\n'
                'exit "$DIAGNOSTIC_CANDIDATE_STATUS"\n')
            python.chmod(0o755)
            environment.update({"UPGRADE_DIAGNOSTIC_CONTRACT": "true",
                "DIAGNOSTIC_SOURCE_DONE": str(root / "source-done"),
                "DIAGNOSTIC_SOURCE_STATUS": str(diagnostics[0]),
                "DIAGNOSTIC_CANDIDATE_STATUS": str(diagnostics[1])})
        result = subprocess.run(
            [
                str(RUNNER),
                str(root / "source.apk"),
                str(root / "candidate.apk"),
                str(root / "test.apk"),
                "de.example.autosecretary",
                "123",
                "schema-8-floor",
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
        source = calls.index(f"install {root / 'source.apk'}")
        test_apk = calls.index(f"install {root / 'test.apk'}")
        upgraded_candidate = calls.index(f"install -r {root / 'candidate.apk'}")
        self.assertLess(fresh_candidate, uninstall)
        self.assertLess(uninstall, source)
        self.assertLess(source, test_apk)
        self.assertLess(test_apk, upgraded_candidate)
        self.assertEqual(
            2,
            sum(call.startswith("shell am instrument ") for call in calls),
        )
        probe_calls = [call for call in calls if call.startswith("shell am instrument ")]
        self.assertTrue(all(
            call.endswith(
                "de.example.autosecretary.test/"
                "de.example.autosecretary.UpgradeProbeInstrumentation"
            )
            for call in probe_calls
        ))
        self.assertTrue(all(" -e class " not in call for call in probe_calls))
        self.assertTrue(all(
            "-e upgradeFixture schema-8-floor" in call for call in probe_calls
        ))
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

    def test_old_unsupported_source_is_explicit_but_candidate_must_support_diagnosis(self):
        root, result, calls = self.run_runner(diagnostics=(4, 0))
        self.assertEqual(0, result.returncode, result.stderr)
        upgrade = calls.index(f"install -r {root / 'candidate.apk'}")
        self.assertLess(calls.index("diagnostic source"), upgrade)
        self.assertLess(upgrade, calls.index("diagnostic candidate"))
        verify = next(i for i, call in enumerate(calls) if "-e upgradePhase verify" in call)
        self.assertLess(calls.index("diagnostic candidate"), verify)

    def test_unexplained_source_crash_and_unsupported_candidate_block_upgrade_gate(self):
        for statuses in [(1, 0), (4, 4), (0, 1)]:
            with self.subTest(statuses=statuses):
                root, result, calls = self.run_runner(diagnostics=statuses)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(any("-e upgradePhase verify" in call for call in calls))
                if statuses[0] == 1:
                    self.assertNotIn(f"install -r {root / 'candidate.apk'}", calls)

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

    def test_invalid_fixture_id_fails_before_adb(self):
        root, result, calls = self.run_runner()
        environment = os.environ.copy()
        environment["PATH"] = f"{root}:{environment['PATH']}"
        environment["ADB_LOG"] = str(root / "invalid-adb.log")
        result = subprocess.run(
            [
                str(RUNNER),
                str(root / "source.apk"),
                str(root / "candidate.apk"),
                str(root / "test.apk"),
                "de.example.autosecretary",
                "123",
                "-invalid",
            ],
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("Invalid fixture ID", result.stderr)
        self.assertFalse((root / "invalid-adb.log").exists())


if __name__ == "__main__":
    unittest.main()
