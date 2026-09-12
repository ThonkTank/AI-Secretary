#!/usr/bin/env python3
"""Validate selected job outcomes and the classifier contract, failing closed."""
from __future__ import annotations

import argparse
import json
import os

from change_scope import ChangeScope, POLICY_VERSION


def validate(stage: str, needs: dict, *, event: str, ref: str) -> None:
    def result(job: str, expected: str = "success") -> None:
        actual = needs.get(job, {}).get("result")
        if actual != expected:
            raise ValueError(f"{job}: expected {expected}, got {actual!r}")

    result("release_scope")
    outputs = needs["release_scope"].get("outputs", {})
    if outputs.get("verification_policy") != POLICY_VERSION:
        raise ValueError("Unsupported or missing policy version")
    if outputs.get("release_required") not in {"true", "false"}:
        raise ValueError("Missing release decision")
    scope = ChangeScope(outputs.get("verification_profile"), outputs["release_required"] == "true")
    expected = dict(line.split("=", 1) for line in scope.github_output().splitlines())
    for key, value in expected.items():
        if key == "verification_reasons":
            continue
        if outputs.get(key) != value:
            raise ValueError(f"Inconsistent scope output: {key}")
    reuse = outputs.get("reuse_pr_verification")
    if reuse not in {"true", "false"}:
        raise ValueError("Missing reuse decision")
    if reuse == "true":
        if event != "push" or ref != "refs/heads/main":
            raise ValueError("Reuse is only allowed on main push")
        if not all(outputs.get(key) for key in (
                "verification_pr_number", "verification_pr_head_sha", "verification_run_id")):
            raise ValueError("Reuse proof identifiers missing")
    if stage == "policy":
        return
    if stage == "publish":
        if not scope.release_required or event == "pull_request" or ref != "refs/heads/main":
            raise ValueError("Publication requires a release on main")
        result("package")
        if needs["package"].get("outputs", {}).get("already_published") != "false":
            raise ValueError("Missing or already-published candidate")
        result("current-upgrade")
        result("upgrade", "success" if scope.historical_upgrades_required else "skipped")
        return
    result("verification-policy")

    def selected(job: str, required: bool) -> None:
        result(job, "success" if required and reuse != "true" else "skipped")

    if stage == "quality":
        if not scope.quality_required:
            raise ValueError("Quality aggregate must be skipped for docs")
        selected("quality-contracts", True)
        selected("quality-goldens", scope.goldens_required)
        selected("quality-build", scope.build_required)
    elif stage in {"android", "pr"}:
        result("quality", "success" if scope.quality_required else "skipped")
        if stage == "android":
            selected("instrumentation", scope.normal_instrumentation_required)
            selected("animation-instrumentation", scope.instrumentation_required)
        else:
            if event != "pull_request":
                raise ValueError("PR gate requires a pull_request event")
            result("instrumentation-gate")
    else:
        raise ValueError(f"Unknown gate stage: {stage}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("policy", "quality", "android", "pr", "publish"))
    args = parser.parse_args()
    try:
        validate(args.stage, json.loads(os.environ["VERIFICATION_NEEDS"]),
                 event=os.environ["GITHUB_EVENT_NAME"], ref=os.environ["GITHUB_REF"])
    except (ValueError, KeyError, TypeError, AttributeError) as error:
        parser.exit(1, f"Verification denied: {error}\n")
    print(f"{args.stage}: selected verification contract satisfied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
