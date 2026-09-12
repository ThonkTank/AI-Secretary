#!/usr/bin/env python3
"""Fail-closed proof that a main squash has already passed the selected PR gate."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
import re
import subprocess
from typing import Callable, Iterable, Mapping, Sequence


from change_scope import POLICY_VERSION, policy_job, required_jobs


@dataclass(frozen=True)
class ReuseDecision:
    reuse: bool
    reason: str
    pr_number: int | None = None
    pr_head_sha: str = ""
    run_id: int | None = None

    @staticmethod
    def denied(reason: str) -> "ReuseDecision":
        return ReuseDecision(False, reason)


def decide(
    *,
    event_name: str,
    ref: str,
    release_required: bool,
    profile: str,
    policy_version: str,
    main_sha: str,
    main_tree: str,
    pulls: Sequence[Mapping[str, object]],
    head_trees: Mapping[str, str],
    runs: Sequence[Mapping[str, object]],
    jobs_by_run: Mapping[int, Sequence[Mapping[str, object]]],
) -> ReuseDecision:
    if event_name != "push" or ref != "refs/heads/main":
        return ReuseDecision.denied("not_main_push")
    try:
        policy_job(profile, policy_version)
        if release_required and profile in {"docs", "host"}:
            return ReuseDecision.denied("invalid_profile_release")
    except ValueError:
        return ReuseDecision.denied("unsupported_policy_or_profile")
    merged = [
        pull
        for pull in pulls
        if pull.get("merged_at")
        and pull.get("merge_commit_sha") == main_sha
        and _nested(pull, "base", "ref") == "main"
        and isinstance(_nested(pull, "head", "sha"), str)
    ]
    if len(merged) != 1:
        return ReuseDecision.denied("merged_pr_not_unique")
    pull = merged[0]
    head_sha = str(_nested(pull, "head", "sha"))
    if not re.fullmatch(r"[0-9a-f]{40}", main_tree) or head_trees.get(head_sha) != main_tree:
        return ReuseDecision.denied("tree_mismatch")
    eligible = [
        run
        for run in runs
        if run.get("event") == "pull_request"
        and run.get("head_sha") == head_sha
        and isinstance(run.get("id"), int)
    ]
    eligible.sort(key=lambda run: (str(run.get("created_at", "")), int(run["id"])),
                  reverse=True)
    # A later failed/incomplete run must not be hidden by an older green one.
    for run in eligible[:1]:
        if run.get("status") != "completed" or run.get("conclusion") != "success":
            return ReuseDecision.denied("latest_pr_run_not_green")
        run_id = int(run["id"])
        jobs = jobs_by_run.get(run_id, ())
        content_job = f"verification-content ({main_tree})"
        expected = (*required_jobs(profile), content_job)
        selected = {name: [job for job in jobs if job.get("name") == name]
                    for name in expected}
        witnesses = [job.get("name") for job in jobs
                     if str(job.get("name", "")).startswith("verification-policy (")]
        content_witnesses = [job.get("name") for job in jobs
                             if str(job.get("name", "")).startswith("verification-content (")]
        if (witnesses == [policy_job(profile, policy_version)]
                and content_witnesses == [content_job]
                and all(len(matches) == 1
                        and matches[0].get("status") == "completed"
                        and matches[0].get("conclusion") == "success"
                        for matches in selected.values())):
            return ReuseDecision(
                True,
                "identical_tree_and_green_pr",
                int(pull["number"]),
                head_sha,
                run_id,
            )
    return ReuseDecision.denied("selected_pr_evidence_missing")


def live_decision(
    *,
    event_name: str,
    ref: str,
    release_required: bool,
    profile: str,
    policy_version: str,
    repository: str,
    main_sha: str,
    workflow: str,
    request: Callable[[str], object],
) -> ReuseDecision:
    if event_name != "push" or ref != "refs/heads/main":
        return ReuseDecision.denied("not_main_push")
    try:
        policy_job(profile, policy_version)
        if release_required and profile in {"docs", "host"}:
            return ReuseDecision.denied("invalid_profile_release")
    except ValueError:
        return ReuseDecision.denied("unsupported_policy_or_profile")
    try:
        main_commit = _mapping(request(f"repos/{repository}/git/commits/{main_sha}"))
        pulls = _sequence(request(f"repos/{repository}/commits/{main_sha}/pulls?per_page=100"))
        if len(pulls) >= 100:
            raise ValueError("Associated pull request evidence may be truncated")
        merged = [
            pull
            for pull in pulls
            if pull.get("merged_at")
            and pull.get("merge_commit_sha") == main_sha
            and _nested(pull, "base", "ref") == "main"
        ]
        head_trees: dict[str, str] = {}
        runs: list[Mapping[str, object]] = []
        jobs_by_run: dict[int, Sequence[Mapping[str, object]]] = {}
        for pull in merged:
            head_sha = _nested(pull, "head", "sha")
            if not isinstance(head_sha, str) or not head_sha:
                continue
            head_commit = _mapping(request(
                f"repos/{repository}/git/commits/{head_sha}"
            ))
            head_tree = _nested(head_commit, "tree", "sha")
            if isinstance(head_tree, str):
                head_trees[head_sha] = head_tree
            payload = _mapping(request(
                f"repos/{repository}/actions/workflows/{workflow}/runs"
                f"?head_sha={head_sha}&event=pull_request&per_page=100"
            ))
            for run in _sequence(payload.get("workflow_runs", [])):
                runs.append(run)
                run_id = run.get("id")
                if (run.get("status") == "completed"
                        and run.get("conclusion") == "success"
                        and isinstance(run_id, int)):
                    jobs = _mapping(request(
                        f"repos/{repository}/actions/runs/{run_id}/jobs?per_page=100"
                    ))
                    listed_jobs = _sequence(jobs.get("jobs", []))
                    if jobs.get("total_count") != len(listed_jobs):
                        raise ValueError("PR job evidence is truncated or lacks a count")
                    jobs_by_run[run_id] = listed_jobs
        main_tree = _nested(main_commit, "tree", "sha")
        return decide(
            event_name=event_name,
            ref=ref,
            release_required=release_required,
            profile=profile,
            policy_version=policy_version,
            main_sha=main_sha,
            main_tree=main_tree if isinstance(main_tree, str) else "",
            pulls=pulls,
            head_trees=head_trees,
            runs=runs,
            jobs_by_run=jobs_by_run,
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError,
            subprocess.SubprocessError, OSError):
        return ReuseDecision.denied("evidence_unavailable")


def gh_request(endpoint: str) -> object:
    completed = subprocess.run(
        ["gh", "api", endpoint],
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(completed.stdout)


def write_outputs(decision: ReuseDecision, destination: str | None) -> None:
    values = {
        "reuse_pr_verification": str(decision.reuse).lower(),
        "verification_reason": decision.reason,
        "verification_pr_number": "" if decision.pr_number is None
            else str(decision.pr_number),
        "verification_pr_head_sha": decision.pr_head_sha,
        "verification_run_id": "" if decision.run_id is None else str(decision.run_id),
    }
    content = "".join(f"{key}={value}\n" for key, value in values.items())
    if destination:
        with open(destination, "a", encoding="utf-8") as output:
            output.write(content)
    else:
        print(content, end="")


def _nested(value: Mapping[str, object], *path: str) -> object:
    current: object = value
    for key in path:
        if not isinstance(current, Mapping):
            return None
        current = current.get(key)
    return current


def _mapping(value: object) -> Mapping[str, object]:
    if not isinstance(value, Mapping):
        raise TypeError("Expected a JSON object")
    return value


def _sequence(value: object) -> Sequence[Mapping[str, object]]:
    if not isinstance(value, list) or any(not isinstance(item, Mapping) for item in value):
        raise TypeError("Expected a JSON object list")
    return value


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Prove whether main can reuse a content-identical green PR verification."
    )
    parser.add_argument("--event-name", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--release-required", choices=("true", "false"), required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--policy-version", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--main-sha", required=True)
    parser.add_argument("--workflow", default="verify.yml")
    arguments = parser.parse_args(argv)
    decision = live_decision(
        event_name=arguments.event_name,
        ref=arguments.ref,
        release_required=arguments.release_required == "true",
        profile=arguments.profile,
        policy_version=arguments.policy_version,
        repository=arguments.repository,
        main_sha=arguments.main_sha,
        workflow=arguments.workflow,
        request=gh_request,
    )
    write_outputs(decision, os.environ.get("GITHUB_OUTPUT"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
