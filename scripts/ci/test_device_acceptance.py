import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "ci" / "run-device-acceptance.sh"


class DeviceAcceptanceTest(unittest.TestCase):
    def test_in_app_update_produces_screenshot_and_machine_report(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            release = temporary / "release"
            release.mkdir()
            apk = release / "AutoSecretary.apk"
            apk.write_bytes(b"signed-production-candidate")
            self._metadata(release / "release-metadata.json", 1010501, "0.2.105", apk)
            previous = temporary / "previous.json"
            self._metadata(previous, 1010401, "0.2.104", apk)
            adb_log = temporary / "adb-calls.txt"
            dump_count = temporary / "dump-count.txt"
            self._executable(temporary / "adb", textwrap.dedent("""\
                #!/usr/bin/env bash
                printf '%s\n' "$*" >> "$ADB_CALL_LOG"
                if [ "$1" = devices ]; then
                  printf 'List of devices attached\nserial-1 device product:pixel\n'
                elif [ "$3" = shell ] && [ "$4" = dumpsys ]; then
                  count=0
                  if [ -f "$DUMP_COUNT" ]; then count=$(cat "$DUMP_COUNT"); fi
                  printf '%s' "$((count + 1))" > "$DUMP_COUNT"
                  if [ "$count" -eq 0 ]; then
                    printf ' versionCode=1010401 minSdk=26\n versionName=0.2.104\n'
                  else
                    printf ' versionCode=1010501 minSdk=26\n versionName=0.2.105\n'
                  fi
                elif [ "$3" = exec-out ]; then
                  printf 'PNG'
                elif [ "$3" = pull ]; then
                  printf '<hierarchy />\n' > "$5"
                else
                  printf 'ok\n'
                fi
                """))
            report_root = temporary / "reports"
            environment = os.environ.copy()
            environment.update({
                "ADB_CALL_LOG": str(adb_log),
                "DUMP_COUNT": str(dump_count),
                "DEVICE_ACCEPTANCE_RELEASE_DIR": str(release),
                "DEVICE_ACCEPTANCE_PREVIOUS_METADATA": str(previous),
                "DEVICE_ACCEPTANCE_REPORT_ROOT": str(report_root),
                "PATH": str(temporary) + os.pathsep + environment["PATH"],
            })

            result = subprocess.run([str(RUNNER), "forest-android-1010501"], cwd=ROOT,
                                    env=environment, input="\nACCEPTED\n", text=True,
                                    capture_output=True, check=False)

            self.assertEqual(0, result.returncode, result.stderr)
            report_dir = report_root / "forest-android-1010501"
            report = json.loads((report_dir / "report.json").read_text())
            self.assertEqual("accepted", report["status"])
            self.assertEqual("1010401", report["installedBefore"]["versionCode"])
            self.assertEqual("1010501", report["installedAfter"]["versionCode"])
            self.assertTrue(report["manualChecks"]["dataPreserved"])
            self.assertTrue((report_dir / "screenshot.png").is_file())
            self.assertTrue((report_dir / "ui-hierarchy.xml").is_file())
            self.assertNotIn(" install ", " " + adb_log.read_text() + " ")

    def test_missing_device_is_reported_as_pending(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            release = temporary / "release"
            release.mkdir()
            apk = release / "AutoSecretary.apk"
            apk.write_bytes(b"candidate")
            self._metadata(release / "release-metadata.json", 1010501, "0.2.105", apk)
            previous = temporary / "previous.json"
            self._metadata(previous, 1010401, "0.2.104", apk)
            self._executable(temporary / "adb", "#!/usr/bin/env bash\nprintf 'List of devices attached\\n'\n")
            report_root = temporary / "reports"
            environment = os.environ.copy()
            environment.update({
                "DEVICE_ACCEPTANCE_RELEASE_DIR": str(release),
                "DEVICE_ACCEPTANCE_PREVIOUS_METADATA": str(previous),
                "DEVICE_ACCEPTANCE_REPORT_ROOT": str(report_root),
                "PATH": str(temporary) + os.pathsep + environment["PATH"],
            })

            result = subprocess.run([str(RUNNER), "forest-android-1010501"], cwd=ROOT,
                                    env=environment, text=True, capture_output=True,
                                    check=False)

            self.assertNotEqual(0, result.returncode)
            report = json.loads((report_root / "forest-android-1010501" / "report.json").read_text())
            self.assertEqual("pending", report["status"])
            self.assertIn("exactly one", report["reason"])

    @staticmethod
    def _metadata(path: pathlib.Path, code: int, name: str, apk: pathlib.Path):
        path.write_text(json.dumps({
            "schemaVersion": 1,
            "versionCode": code,
            "versionName": name,
            "packageName": "de.thonktank.autosecretary",
            "apkSizeBytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "commitSha": "b7cbd4a49361ff8999f1c416799017aa19024e56",
        }), encoding="utf-8")

    @staticmethod
    def _executable(path: pathlib.Path, contents: str):
        path.write_text(contents, encoding="utf-8")
        path.chmod(0o755)


if __name__ == "__main__":
    unittest.main()
