import copy
import unittest

from change_scope import ChangeScope
from verification_gate import validate


def evidence(profile, reuse=False):
    scope = ChangeScope(profile, profile in {"today", "full"})
    outputs = dict(line.split("=", 1) for line in scope.github_output().splitlines())
    outputs.update(reuse_pr_verification=str(reuse).lower(), verification_pr_number="7",
                   verification_pr_head_sha="head", verification_run_id="42")
    needs = {"release_scope": {"result": "success", "outputs": outputs},
             "verification-policy": {"result": "success"},
             "instrumentation-gate": {"result": "success"},
             "quality": {"result": "success" if scope.quality_required else "skipped"}}
    for job, required in {
        "quality-contracts": scope.quality_required, "quality-goldens": scope.goldens_required,
        "quality-build": scope.build_required, "instrumentation": scope.normal_instrumentation_required,
        "animation-instrumentation": scope.instrumentation_required,
    }.items():
        needs[job] = {"result": "success" if required and not reuse else "skipped"}
    return needs


class VerificationGateTest(unittest.TestCase):
    def test_all_profiles_and_verified_main_reuse_select_exact_jobs(self):
        for profile in ("docs", "host", "today", "full"):
            for reuse in (False, True):
                for stage in ("policy", "quality", "android", "pr"):
                    if (stage == "quality" and profile == "docs") or (stage == "pr" and reuse):
                        continue
                    with self.subTest(profile=profile, reuse=reuse, stage=stage):
                        validate(stage, evidence(profile, reuse),
                                 event="push" if reuse else "pull_request", ref="refs/heads/main")

    def test_missing_red_or_unexpected_skipped_dependencies_are_denied(self):
        stages = {
            "quality": ("release_scope", "verification-policy", "quality-contracts", "quality-goldens", "quality-build"),
            "android": ("release_scope", "verification-policy", "quality", "instrumentation", "animation-instrumentation"),
            "pr": ("release_scope", "verification-policy", "quality", "instrumentation-gate"),
        }
        for stage, dependencies in stages.items():
            for dependency in dependencies:
                for result in (None, "skipped", "failure", "cancelled"):
                    needs = evidence("full")
                    needs[dependency]["result"] = result
                    with self.subTest(stage=stage, dependency=dependency, result=result):
                        with self.assertRaises(ValueError):
                            validate(stage, needs, event="pull_request", ref="refs/pull/7/merge")

    def test_tampered_scope_outputs_do_not_silently_skip_work(self):
        good = evidence("full")
        for key in good["release_scope"]["outputs"]:
            if key in {"verification_reasons", "verification_pr_number", "verification_pr_head_sha", "verification_run_id"}:
                continue
            needs = copy.deepcopy(good)
            needs["release_scope"]["outputs"][key] = ""
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate("policy", needs, event="pull_request", ref="refs/pull/7/merge")

    def test_reuse_on_pr_manual_run_or_without_proof_is_denied(self):
        for event, ref in (("pull_request", "refs/pull/7/merge"),
                           ("workflow_dispatch", "refs/heads/main"), ("push", "refs/heads/other")):
            with self.assertRaises(ValueError):
                validate("policy", evidence("full", True), event=event, ref=ref)
        for key in ("verification_pr_number", "verification_pr_head_sha", "verification_run_id"):
            needs = evidence("full", True)
            del needs["release_scope"]["outputs"][key]
            with self.assertRaises(ValueError):
                validate("policy", needs, event="push", ref="refs/heads/main")

    def test_an_unselected_job_must_be_skipped(self):
        needs = evidence("today")
        needs["instrumentation"]["result"] = "failure"
        with self.assertRaises(ValueError):
            validate("android", needs, event="pull_request", ref="refs/pull/7/merge")
        needs = evidence("host")
        needs["quality-build"]["result"] = "success"
        with self.assertRaises(ValueError):
            validate("quality", needs, event="pull_request", ref="refs/pull/7/merge")

    def test_publish_requires_current_smoke_and_exact_historical_selection(self):
        for profile in ("today", "full"):
            needs = evidence(profile)
            needs.update(package={"result": "success", "outputs": {"already_published": "false"}},
                         **{"current-upgrade": {"result": "success"},
                            "upgrade": {"result": "success" if profile == "full" else "skipped"}})
            validate("publish", needs, event="push", ref="refs/heads/main")
            for job in ("package", "current-upgrade", "upgrade"):
                for outcome in (None, "failure", "cancelled", "skipped", "success"):
                    if outcome == needs[job]["result"]:
                        continue
                    bad = copy.deepcopy(needs)
                    bad[job]["result"] = outcome
                    with self.subTest(profile=profile, job=job, outcome=outcome), self.assertRaises(ValueError):
                        validate("publish", bad, event="push", ref="refs/heads/main")
            for event, ref in (("pull_request", "refs/heads/main"), ("push", "refs/heads/other")):
                with self.assertRaises(ValueError):
                    validate("publish", needs, event=event, ref=ref)
            needs["package"]["outputs"]["already_published"] = "true"
            with self.assertRaises(ValueError):
                validate("publish", needs, event="push", ref="refs/heads/main")
        for profile in ("docs", "host"):
            with self.assertRaises(ValueError):
                validate("publish", evidence(profile), event="push", ref="refs/heads/main")
