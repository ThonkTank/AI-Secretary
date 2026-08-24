import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "ci" / "run-upgrade-test.sh"


class UpgradeRunnerTest(unittest.TestCase):
    def test_clean_candidate_install_precedes_independent_upgrade_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
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
                      "shell pm path de.example.autosecretary") : ;;
                      "shell am start "*) echo 'Status: ok' ;;
                      "shell dumpsys package de.example.autosecretary") echo '  versionCode=123 minSdk=26 targetSdk=35' ;;
                      "shell am instrument "*) echo 'OK (1 test)' ;;
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

            self.assertEqual(0, result.returncode, result.stderr)
            calls = log.read_text(encoding="utf-8").splitlines()
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
                sum(call == "shell pm path de.example.autosecretary" for call in calls),
            )


if __name__ == "__main__":
    unittest.main()
