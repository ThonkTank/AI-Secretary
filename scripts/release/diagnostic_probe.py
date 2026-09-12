#!/usr/bin/env python3
"""Read the installed app through its early diagnostic contract; never seed or reset its data."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time

NAMESPACE = "de.thonktank.autosecretary"


def interpret(output: str) -> dict:
    protocol = re.search(r"^INSTRUMENTATION_RESULT: diagnosticProtocol=(\d+)\s*$", output, re.MULTILINE)
    unsupported = re.search(r"^INSTRUMENTATION_RESULT: diagnosticUnsupported=(.+)$", output, re.MULTILINE)
    success = "OK (1 diagnostic probe)" in output
    if unsupported:
        if success or not protocol or protocol[1] != "1" or "UNSUPPORTED_DIAGNOSTIC_PROTOCOL" not in output:
            raise ValueError("Ambiguous diagnostic compatibility result")
        return {"status": "unsupported", "requestedProtocol": 1, "reason": unsupported[1].strip()}
    diagnosis = re.search(r"^INSTRUMENTATION_RESULT: diagnosis=(.+)$", output, re.MULTILINE)
    if (not success or not protocol or protocol[1] != "1" or not diagnosis
            or any(error in output for error in ("FAIL:", "INSTRUMENTATION_FAILED", "Process crashed"))):
        raise ValueError("Diagnostic did not complete with a supported early contract")
    return {"status": "supported", "protocol": 1, "diagnosis": diagnosis[1].strip()}


def diagnose(serial: str, package: str) -> dict:
    if package not in {NAMESPACE, NAMESPACE + ".test"}:
        raise ValueError("Unsupported target identity")
    prefix = ["adb", "-s", serial]

    def shell(*args: str, timeout: int = 30, check: bool = True) -> subprocess.CompletedProcess:
        return subprocess.run([*prefix, "shell", shlex.join(args)], stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, timeout=timeout, check=check)

    component = package + ".test/" + NAMESPACE + ".DiagnosticProbeInstrumentation"
    declarations = shell("pm", "list", "instrumentation").stdout.splitlines()
    if f"instrumentation:{component} (target={package})" not in declarations:
        raise ValueError("Install the matching signed helper with the dedicated diagnostic component first")
    package_dump = shell("dumpsys", "package", package).stdout
    version = re.search(r"\bversionCode=(\d+)\b", package_dump)
    if not version:
        raise ValueError("Installed target version is unavailable")
    # No force-stop, seed, legacy diagnose, component changes, or no-restart option here.
    process = shell("am", "instrument", "-w", "-r", component, timeout=45, check=False)
    result = interpret(process.stdout)
    if process.returncode != 0 and result["status"] == "supported":
        raise ValueError("Diagnostic transport failed despite a success marker")
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        state = shell("pidof", package, check=False)
        if state.returncode in (0, 1) and not state.stdout.strip():
            return {**result, "serial": serial, "package": package, "versionCode": int(version[1]),
                    "processExited": True}
        time.sleep(0.1)
    raise ValueError("Diagnostic process did not exit; a normal restart is not yet proved safe")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--package", default=NAMESPACE, choices=(NAMESPACE, NAMESPACE + ".test"))
    parser.add_argument("--report-file", type=Path)
    args = parser.parse_args()
    if not args.serial:
        listing = subprocess.check_output(["adb", "devices"], text=True)
        serials = re.findall(r"^(\S+)\s+device$", listing, flags=re.MULTILINE)
        if len(serials) != 1:
            parser.error("Choose exactly one connected device with --serial")
        args.serial = serials[0]
    result = diagnose(args.serial, args.package)
    text = json.dumps(result, indent=2) + "\n"
    if args.report_file:
        args.report_file.parent.mkdir(parents=True, exist_ok=True)
        args.report_file.write_text(text)
    print(text, end="")
    return 0 if result["status"] == "supported" else 4


if __name__ == "__main__":
    raise SystemExit(main())
