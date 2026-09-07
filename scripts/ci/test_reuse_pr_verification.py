import unittest

from reuse_pr_verification import ReuseDecision, decide, live_decision


MAIN = "main-sha"
HEAD = "head-sha"
TREE = "tree-sha"
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


def jobs(conclusion="success"):
    return [
        {"name": name, "status": "completed", "conclusion": conclusion}
        for name in ("quality", "instrumentation-gate", "pull-request-gate")
    ]


def decision(**overrides):
    values = dict(
        event_name="push",
        ref="refs/heads/main",
        release_required=True,
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

    def test_non_main_manual_and_non_product_runs_never_reuse(self):
        self.assertEqual("not_main_push", decision(event_name="pull_request").reason)
        self.assertEqual("not_main_push", decision(ref="refs/heads/other").reason)
        self.assertEqual("not_product_change", decision(release_required=False).reason)

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

    def test_all_three_product_jobs_must_have_succeeded(self):
        for name in ("quality", "instrumentation-gate", "pull-request-gate"):
            with self.subTest(name=name):
                incomplete = jobs()
                for job in incomplete:
                    if job["name"] == name:
                        job["conclusion"] = "skipped"
                self.assertEqual(
                    "green_product_pr_run_missing",
                    decision(jobs_by_run={RUN: incomplete}).reason,
                )

    def test_latest_bad_run_does_not_hide_an_older_green_rerun(self):
        newer = run(run_id=43, created="2026-09-08")
        selected = decision(
            runs=[newer, run()],
            jobs_by_run={43: jobs("failure"), RUN: jobs()},
        )
        self.assertTrue(selected.reuse)
        self.assertEqual(RUN, selected.run_id)

    def test_failed_or_wrong_head_workflow_is_not_evidence(self):
        self.assertEqual(
            "green_product_pr_run_missing",
            decision(runs=[run(conclusion="failure")]).reason,
        )
        self.assertEqual(
            "green_product_pr_run_missing",
            decision(runs=[run(head_sha="other")]).reason,
        )

    def test_live_api_failure_is_fail_closed(self):
        def broken(_endpoint):
            raise OSError("offline")

        result = live_decision(
            event_name="push",
            ref="refs/heads/main",
            release_required=True,
            repository="owner/repo",
            main_sha=MAIN,
            workflow="verify.yml",
            request=broken,
        )
        self.assertFalse(result.reuse)
        self.assertEqual("evidence_unavailable", result.reason)


if __name__ == "__main__":
    unittest.main()
