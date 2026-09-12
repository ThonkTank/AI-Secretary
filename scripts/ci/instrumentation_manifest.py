#!/usr/bin/env python3
"""Validate each instrumentation component/target pair from aapt's actual APK manifest."""
from __future__ import annotations

import argparse
import re
import sys

NAMESPACE = "de.thonktank.autosecretary"
DIAGNOSTIC_RUNNERS = frozenset(NAMESPACE + "." + name for name in (
    "DiagnosticProbeInstrumentation", "DiagnosticFixtureInstrumentation", "NormalDiagnosticRecoveryInstrumentation"))


def required_runners(primary: str) -> frozenset[str]:
    extra = ({NAMESPACE + ".DiagnosticProbeInstrumentation"}
             if primary == NAMESPACE + ".UpgradeProbeInstrumentation" else DIAGNOSTIC_RUNNERS)
    return frozenset(extra | {primary})


def validate(manifest: str, target: str, primary: str) -> dict[str, str]:
    runners: dict[str, str] = {}
    current: dict[str, str] | None = None
    level = 0

    def finish() -> None:
        if current is None:
            return
        if set(current) != {"name", "targetPackage"} or current["name"] in runners:
            raise ValueError(f"Incomplete or duplicate instrumentation: {current}")
        runners[current["name"]] = current["targetPackage"]

    for line in manifest.splitlines():
        indent = len(line) - len(line.lstrip())
        if current is not None and indent <= level and line.strip():
            finish()
            current = None
        if re.match(r"\s*E: instrumentation(?:\s|$)", line):
            current, level = {}, indent
        elif current is not None:
            match = re.search(r'android:(name|targetPackage)\([^)]*\)="([^"]+)"', line)
            if match:
                if match[1] in current:
                    raise ValueError("Duplicate instrumentation attribute")
                current[match[1]] = match[2]
    finish()
    expected = required_runners(primary)
    if set(runners) != expected or any(value != target for value in runners.values()):
        raise ValueError(f"Instrumentation identity mismatch: expected={sorted(expected)} -> {target}; actual={runners}")
    return runners


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("regular", "upgrade"), required=True)
    args = parser.parse_args()
    primary = "androidx.test.runner.AndroidJUnitRunner" if args.mode == "regular" else NAMESPACE + ".UpgradeProbeInstrumentation"
    target = NAMESPACE + ".test" if args.mode == "regular" else NAMESPACE
    validate(sys.stdin.read(), target, primary)


if __name__ == "__main__":
    main()
