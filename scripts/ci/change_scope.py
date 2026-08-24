#!/usr/bin/env python3
"""Classify changed repository paths for Android verification and publication."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import sys
from typing import Iterable


BUILD_INPUTS = {
    ".github/workflows/verify.yml",
    "app/build.gradle.kts",
    "build.gradle.kts",
    "core-domain/build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "release/release.properties",
    "scripts/ci/change_scope.py",
    "scripts/ci/prepare-preview-sdk-tools.sh",
    "scripts/ci/run-upgrade-test.sh",
    "scripts/release/release_tool.py",
    "settings.gradle.kts",
    "today-core/build.gradle.kts",
}
INSTRUMENTATION_PREFIXES = (
    ".github/workflows/",
    "app/schemas/",
    "app/src/androidTest/",
    "app/src/debug/",
    "app/src/main/",
    "core-domain/src/main/",
    "gradle/",
    "scripts/ci/",
    "today-core/src/main/",
)
RELEASE_PREFIXES = (
    "app/src/main/",
    "core-domain/src/main/",
    "gradle/",
    "today-core/src/main/",
)


@dataclass(frozen=True)
class ChangeScope:
    quality_required: bool
    instrumentation_required: bool
    release_required: bool

    def github_output(self) -> str:
        return "\n".join(
            (
                f"quality_required={str(self.quality_required).lower()}",
                f"instrumentation_required={str(self.instrumentation_required).lower()}",
                f"release_required={str(self.release_required).lower()}",
            )
        )


def classify(paths: Iterable[str]) -> ChangeScope:
    changed = {_normalize(path) for path in paths}
    changed.discard("")
    relevant = {path for path in changed if not _is_documentation(path)}
    return ChangeScope(
        quality_required=bool(relevant),
        instrumentation_required=any(_requires_instrumentation(path) for path in relevant),
        release_required=any(_requires_release(path) for path in relevant),
    )


def _normalize(path: str) -> str:
    value = path.strip().replace("\\", "/")
    while value.startswith("./"):
        value = value[2:]
    return value


def _is_documentation(path: str) -> bool:
    return path.startswith("docs/") or ("/" not in path and path.endswith(".md"))


def _requires_instrumentation(path: str) -> bool:
    return path in BUILD_INPUTS or path.startswith(INSTRUMENTATION_PREFIXES)


def _requires_release(path: str) -> bool:
    return (
        path in BUILD_INPUTS
        or path.startswith(RELEASE_PREFIXES)
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Print GitHub Actions outputs for changed paths read from stdin."
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="require every gate, for an explicit manual workflow run",
    )
    arguments = parser.parse_args(argv)
    scope = ChangeScope(True, True, True) if arguments.all else classify(sys.stdin)
    print(scope.github_output())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
