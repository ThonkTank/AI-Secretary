import unittest

from change_scope import POLICY_VERSION, policy_job, required_jobs
from reuse_pr_verification import ReuseDecision, decide, live_decision


MAIN = "main-sha"
HEAD = "head-sha"
TREE = "1" * 40
RUN = 42


def pull(number=7, main_sha=MAIN, head_sha=HEAD, base="main", merged=True):
    return {
        "number": number,
        "merged_at": "2026-09-07T10:00:00Z" if merged else None,
        "merge_commit_sha": main_sha,
        "base": {"ref": base},
        "head": {"sha": head_sha},
    }


def run(run_id=RUN, head_sha=HEAD, conclusion="success", created="2026-09-07"):
    return {
        "id": run_id,
        "event": "pull_request",
        "head_sha": head_sha,
        "status": "completed",
        "conclusion": conclusion,
        "created_at": created,
    }


def jobs(conclusion="success", profile="full"):
    return [
        {"name": name, "status": "completed", "conclusion": conclusion}
        for name in (*required_jobs(profile), f"verification-content ({TREE})")
    ]


def decision(**overrides):
    values = dict(
        event_name="push",
        ref="refs/heads/main",
        release_required=True,
        profile="full",
        policy_version=POLICY_VERSION,
        main_sha=MAIN,
        main_tree=TREE,
        pulls=[pull()],
        head_trees={HEAD: TREE},
        runs=[run()],
        jobs_by_run={RUN: jobs()},
    )
    values.update(overrides)
    return decide(**values)


class ReusePrVerificationTest(unittest.TestCase):
    def test_identical_squash_and_green_product_gate_are_reused(self):
        self.assertEqual(
            ReuseDecision(True, "identical_tree_and_green_pr", 7, HEAD, RUN),
            decision(),
        )

    def test_non_main_and_manual_runs_never_reuse(self):
        self.assertEqual("not_main_push", decision(event_name="pull_request").reason)
        self.assertEqual("not_main_push", decision(ref="refs/heads/other").reason)

    def test_non_product_profiles_can_reuse_complete_matching_proof(self):
        for profile in ("docs", "host", "full"):
            self.assertTrue(decision(release_required=False, profile=profile,
                                    jobs_by_run={RUN: jobs(profile=profile)}).reuse)

    def test_profile_and_policy_must_match(self):
        self.assertFalse(decision(profile="today").reuse)
        self.assertFalse(decision(profile="unknown").reuse)
        self.assertFalse(decision(policy_version="old").reuse)
        self.assertFalse(decision(profile="host").reuse)
        self.assertFalse(decision(jobs_by_run={RUN: jobs() + [
            {"name": policy_job("today"), "status": "completed", "conclusion": "success"}
        ]}).reuse)

    def test_merge_pr_must_be_unique_and_target_main(self):
        self.assertEqual("merged_pr_not_unique", decision(pulls=[]).reason)
        self.assertEqual(
            "merged_pr_not_unique",
            decision(pulls=[pull(), pull(number=8)]).reason,
        )
        self.assertEqual(
            "merged_pr_not_unique", decision(pulls=[pull(base="release")]).reason
        )
        self.assertEqual(
            "merged_pr_not_unique", decision(pulls=[pull(merged=False)]).reason
        )

    def test_tree_mismatch_falls_back_to_full_main_verification(self):
        self.assertEqual("tree_mismatch", decision(head_trees={HEAD: "other"}).reason)
        self.assertEqual("tree_mismatch", decision(main_tree="").reason)

    def test_each_selected_job_must_have_succeeded(self):
        for name in required_jobs("full"):
            with self.subTest(name=name):
                incomplete = jobs()
                for job in incomplete:
                    if job["name"] == name:
                        job["conclusion"] = "skipped"
                self.assertEqual(
                    "selected_pr_evidence_missing",
                    decision(jobs_by_run={RUN: incomplete}).reason,
                )

    def test_latest_bad_run_cannot_reuse_an_older_green_run(self):
        newer = run(run_id=43, created="2026-09-08")
        selected = decision(
            runs=[newer, run()],
            jobs_by_run={43: jobs("failure"), RUN: jobs()},
        )
        self.assertFalse(selected.reuse)

    def test_failed_or_wrong_head_workflow_is_not_evidence(self):
        self.assertEqual(
            "latest_pr_run_not_green",
            decision(runs=[run(conclusion="failure")]).reason,
        )
        self.assertEqual(
            "selected_pr_evidence_missing",
            decision(runs=[run(head_sha="other")]).reason,
        )

    def test_missing_duplicate_incomplete_or_red_individual_jobs_are_rejected(self):
        for profile in ("docs", "host", "today", "full"):
            for name in required_jobs(profile):
                good = jobs(profile=profile)
                matched = next(j for j in good if j["name"] == name)
                variants = [
                    [j for j in good if j["name"] != name],
                    good + [dict(matched)],
                ]
                for conclusion in ("failure", "skipped", "cancelled", None):
                    variants.append([dict(j, conclusion=conclusion) if j["name"] == name else j
                                     for j in good])
                variants.append([dict(j, status="in_progress") if j["name"] == name else j
                                 for j in good])
                for evidence in variants:
                    with self.subTest(profile=profile, job=name, evidence=evidence):
                        self.assertFalse(decision(profile=profile,
                            release_required=profile in {"today", "full"},
                            jobs_by_run={RUN: evidence}).reuse)

    def test_tested_merge_tree_must_match_even_when_pr_head_matches_main(self):
        name = f"verification-content ({TREE})"
        good = jobs()
        witness = next(job for job in good if job["name"] == name)
        variants = [
            [job for job in good if job["name"] != name],
            good + [dict(witness)],
            [dict(job, name=f"verification-content ({'2' * 40})")
             if job["name"] == name else job for job in good],
        ]
        for outcome in ("failure", "skipped", "cancelled", None):
            variants.append([dict(job, conclusion=outcome) if job["name"] == name else job
                             for job in good])
        variants.append([dict(job, status="in_progress") if job["name"] == name else job
                         for job in good])
        for evidence in variants:
            with self.subTest(evidence=evidence):
                self.assertFalse(decision(jobs_by_run={RUN: evidence}).reuse)
        self.assertFalse(decision(main_tree="bad", head_trees={HEAD: "bad"}).reuse)

    def test_live_job_evidence_must_be_complete(self):
        for count in (len(jobs()), len(jobs()) + 1, None):
            responses = {
                f"repos/owner/repo/git/commits/{MAIN}": {"tree": {"sha": TREE}},
                f"repos/owner/repo/commits/{MAIN}/pulls?per_page=100": [pull()],
                f"repos/owner/repo/git/commits/{HEAD}": {"tree": {"sha": TREE}},
                f"repos/owner/repo/actions/workflows/verify.yml/runs?head_sha={HEAD}&event=pull_request&per_page=100": {"workflow_runs": [run()]},
                f"repos/owner/repo/actions/runs/{RUN}/jobs?per_page=100": {"jobs": jobs(), "total_count": count},
            }
            result = live_decision(event_name="push", ref="refs/heads/main", release_required=True,
                profile="full", policy_version=POLICY_VERSION, repository="owner/repo", main_sha=MAIN,
                workflow="verify.yml", request=responses.__getitem__)
            self.assertEqual(count == len(jobs()), result.reuse)
            if count != len(jobs()):
                self.assertEqual("evidence_unavailable", result.reason)

    def test_live_api_failure_is_fail_closed(self):
        def broken(_endpoint):
            raise OSError("offline")

        result = live_decision(
            event_name="push",
            ref="refs/heads/main",
            release_required=True,
        profile="full",
        policy_version=POLICY_VERSION,
            repository="owner/repo",
            main_sha=MAIN,
            workflow="verify.yml",
            request=broken,
        )
        self.assertFalse(result.reuse)
        self.assertEqual("evidence_unavailable", result.reason)


if __name__ == "__main__":
    unittest.main()
