#!/usr/bin/env python3
"""One versioned, conservative verification policy for local, PR, main and release gates."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import re
import subprocess
import sys
from typing import Iterable

POLICY_VERSION = "1"
PROFILES = frozenset({"docs", "host", "today", "full"})
TODAY_PRODUCTION = frozenset({
    "today-core/src/main/java/de/thonktank/autosecretary/presentation/today/TodayCoordinator.java",
    "today-core/src/main/java/de/thonktank/autosecretary/presentation/today/TodayCommandDispatcher.java",
})
TODAY_TESTS = frozenset({
    "app/src/test/java/de/thonktank/autosecretary/TodayCoordinatorTest.java",
    "app/src/test/java/de/thonktank/autosecretary/TodayCommandDispatcherTest.java",
    "app/src/test/java/de/thonktank/autosecretary/TodayActionIntegrationRobolectricTest.java",
    "app/src/test/java/de/thonktank/autosecretary/FocusTaskViewTest.java",
})
RELEASE_UPGRADE_INPUTS = frozenset({
    "app/src/androidTest/java/de/thonktank/autosecretary/UpgradePersistenceProbe.java",
    "app/src/androidTest/java/de/thonktank/autosecretary/UpgradeProbeInstrumentation.java",
})
NATIVE_LANES = (
    {"api-level": 26, "target": "default", "arch": "x86_64", "channel": "stable"},
    {"api-level": 35, "target": "google_apis", "arch": "x86_64", "channel": "stable"},
    {"api-level": "37.0", "target": "google_apis", "arch": "x86_64", "channel": "canary"},
)


@dataclass(frozen=True)
class Change:
    status: str
    path: str
    old_path: str | None = None


@dataclass(frozen=True)
class ChangeScope:
    profile: str
    release_required: bool
    reasons: tuple[str, ...] = ()

    def __post_init__(self):
        if self.profile not in PROFILES:
            raise ValueError(f"Unknown verification profile: {self.profile}")
        if self.release_required and self.profile in {"docs", "host"}:
            raise ValueError("Non-product profiles cannot publish")

    @property
    def quality_required(self) -> bool:
        return self.profile != "docs"

    @property
    def instrumentation_required(self) -> bool:
        return self.profile in {"today", "full"}

    @property
    def build_required(self) -> bool:
        return self.profile in {"today", "full"}

    @property
    def goldens_required(self) -> bool:
        # Host-only changes run the complete host suite once in the contracts job.
        return self.profile == "full"

    @property
    def normal_instrumentation_required(self) -> bool:
        return self.profile == "full"

    @property
    def historical_upgrades_required(self) -> bool:
        return self.release_required and self.profile == "full"

    @property
    def animation_matrix(self) -> dict:
        lanes = NATIVE_LANES if self.profile == "full" else (NATIVE_LANES[1],)
        return {"include": list(lanes)}

    def github_output(self) -> str:
        values = {
            "verification_profile": self.profile,
            "verification_policy": POLICY_VERSION,
            "verification_reasons": json.dumps(self.reasons, ensure_ascii=True),
            "quality_required": str(self.quality_required).lower(),
            "build_required": str(self.build_required).lower(),
            "goldens_required": str(self.goldens_required).lower(),
            "instrumentation_required": str(self.instrumentation_required).lower(),
            "normal_instrumentation_required": str(self.normal_instrumentation_required).lower(),
            "historical_upgrades_required": str(self.historical_upgrades_required).lower(),
            "release_required": str(self.release_required).lower(),
            "animation_matrix": json.dumps(self.animation_matrix, separators=(",", ":")),
        }
        return "\n".join(f"{key}={value}" for key, value in values.items())


def policy_job(profile: str, version: str = POLICY_VERSION) -> str:
    if profile not in PROFILES or version != POLICY_VERSION:
        raise ValueError("Unsupported verification policy/profile")
    return f"verification-policy ({version}, {profile})"


def required_jobs(profile: str) -> frozenset[str]:
    scope = ChangeScope(profile, profile in {"today", "full"})
    result = {"release_scope", policy_job(profile), "instrumentation-gate", "pull-request-gate"}
    if scope.quality_required:
        result.update({"quality", "quality-contracts"})
    if scope.goldens_required:
        result.add("quality-goldens")
    if scope.build_required:
        result.add("quality-build")
    if scope.instrumentation_required:
        for lane in scope.animation_matrix["include"]:
            identity = ", ".join(str(lane[k]) for k in ("api-level", "target", "arch", "channel"))
            result.add(f"animation-instrumentation ({identity})")
    if scope.normal_instrumentation_required:
        for lane in NATIVE_LANES:
            identity = ", ".join(str(lane[k]) for k in ("api-level", "target", "arch", "channel"))
            result.add(f"instrumentation ({identity})")
    return frozenset(result)


def _is_documentation(path: str) -> bool:
    return path.startswith("docs/") or ("/" not in path and path.endswith(".md"))


def _is_host_test(path: str) -> bool:
    return path.startswith(("app/src/test/", "core-domain/src/test/", "today-core/src/test/")) or (
        path.startswith(("scripts/ci/", "scripts/release/"))
        and path.rsplit("/", 1)[-1].startswith("test_") and path.endswith(".py"))


def _requires_release(path: str) -> bool:
    if path in RELEASE_UPGRADE_INPUTS:
        return True
    if _is_documentation(path) or _is_host_test(path):
        return False
    if path.startswith(("app/src/androidTest/", "app/src/debug/", "app/schemas/")):
        return False
    if path == "app/proguard-debug.pro":
        return False
    # An unknown input can affect packaging. Never silently suppress its release gates.
    return True


def _valid_path(path: str) -> bool:
    return (bool(path) and path == path.strip() and "\\" not in path
            and "\n" not in path and "\r" not in path and not path.startswith("/")
            and ".." not in path.split("/"))


def classify_changes(changes: Iterable[Change]) -> ChangeScope:
    values = tuple(changes)
    if any(not _valid_path(c.path) or (c.old_path is not None and not _valid_path(c.old_path))
           or re.fullmatch(r"[AMDTU]|[RC](?:100|[0-9]{1,2})", c.status) is None
           or (c.status.startswith(("R", "C")) != (c.old_path is not None)) for c in values):
        return ChangeScope("full", True, ("invalid_or_unknown_diff_entry",))
    relevant = tuple(c for c in values if not (_is_documentation(c.path)
                     and (c.old_path is None or _is_documentation(c.old_path))))
    if not relevant:
        return ChangeScope("docs", False, ("documentation_or_empty_diff",))
    release = any(_requires_release(p) for c in relevant for p in (c.path, c.old_path) if p)
    structural = tuple(c for c in relevant if c.status != "M" and c.status != "A")
    if structural:
        return ChangeScope("full", release, tuple(f"structural_change:{c.status}:{c.path}" for c in structural))
    if all(_is_host_test(c.path) for c in relevant):
        return ChangeScope("host", False, ("host_tests_only",))
    if (all(c.path in TODAY_PRODUCTION | TODAY_TESTS for c in relevant)
            and all(c.status == "M" for c in relevant if c.path in TODAY_PRODUCTION)):
        return ChangeScope("today", True, ("audited_today_action_forwarding",))
    return ChangeScope("full", release, tuple(f"full_scope:{c.status}:{c.path}" for c in relevant))


def classify(paths: Iterable[str]) -> ChangeScope:
    """Legacy path-list adapter. CI/local entrypoints use the status-aware Git reader below."""
    return classify_changes(Change("M", path.rstrip("\n")) for path in paths if path.rstrip("\n"))


def parse_name_status(raw: str) -> tuple[Change, ...]:
    fields = raw.split("\0")
    if fields[-1] != "":
        raise ValueError("Git name-status output is not NUL terminated")
    fields.pop()
    result = []
    while fields:
        status = fields.pop(0)
        if not fields:
            raise ValueError("Missing diff path")
        path = fields.pop(0)
        if status.startswith(("R", "C")):
            if not fields:
                raise ValueError("Missing rename destination")
            result.append(Change(status, fields.pop(0), path))
        else:
            result.append(Change(status, path))
    return tuple(result)


def git_scope(base: str, head: str = "HEAD", *, working_tree: bool = False,
              cwd: str | None = None) -> ChangeScope:
    try:
        def git(*arguments: str) -> str:
            return subprocess.check_output(["git", *arguments], text=True, stderr=subprocess.PIPE, cwd=cwd)

        def commit(ref: str) -> str:
            value = git("rev-parse", "--verify", "--end-of-options", ref + "^{commit}").strip()
            if re.fullmatch(r"[0-9a-f]{40,64}", value) is None:
                raise ValueError("Invalid resolved commit")
            return value

        arguments = ["diff", "--name-status", "-z", "--find-renames", commit(base)]
        if not working_tree:
            arguments.append(commit(head))
        arguments.append("--")
        changes = list(parse_name_status(git(*arguments)))
        if working_tree:
            changes.extend(Change("A", path) for path in
                           git("ls-files", "--others", "--exclude-standard", "-z").split("\0") if path)
        return classify_changes(changes)
    except (OSError, subprocess.SubprocessError, ValueError, UnicodeError):
        return ChangeScope("full", True, ("diff_evidence_unavailable",))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="Explicit full verification")
    parser.add_argument("--base", help="Git base used for status-aware classification")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--working-tree", action="store_true", help="Include local edits and untracked files")
    parser.add_argument("--profile-only", action="store_true")
    args = parser.parse_args(argv)
    if args.all:
        scope = ChangeScope("full", True, ("explicit_full_verification",))
    elif args.base:
        scope = git_scope(args.base, args.head, working_tree=args.working_tree)
    elif args.working_tree:
        scope = ChangeScope("full", True, ("missing_diff_base",))
    else:
        scope = classify(sys.stdin)
    print(scope.profile if args.profile_only else scope.github_output())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
