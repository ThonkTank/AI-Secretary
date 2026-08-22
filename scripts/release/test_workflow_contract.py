import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github" / "workflows" / "verify.yml").read_text(encoding="utf-8")


class WorkflowContractTest(unittest.TestCase):
    def test_change_scope_separates_quality_instrumentation_and_release(self):
        release_scope = WORKFLOW.split("\n  release_scope:", 1)[1].split(
            "\n  quality:", 1
        )[0]
        quality = WORKFLOW.split("\n  quality:", 1)[1].split(
            "\n  instrumentation:", 1
        )[0]
        instrumentation = WORKFLOW.split("\n  instrumentation:", 1)[1].split(
            "\n  instrumentation-gate:", 1
        )[0]
        package = WORKFLOW.split("\n  package:", 1)[1].split("\n  upgrade:", 1)[0]

        self.assertIn("quality_required:", release_scope)
        self.assertIn("instrumentation_required:", release_scope)
        self.assertIn("release_required:", release_scope)
        self.assertIn("python3 scripts/ci/change_scope.py", release_scope)
        self.assertNotIn("app_changed", WORKFLOW)
        self.assertIn("outputs.quality_required == 'true'", quality)
        self.assertIn("outputs.instrumentation_required == 'true'", instrumentation)
        self.assertNotIn("github.event_name != 'pull_request'", instrumentation)
        self.assertIn("outputs.release_required == 'true'", package)

    def test_signing_alias_and_passwords_are_independent_inputs(self):
        self.assertIn("SIGNING_KEY_ALIAS: ${{ secrets.KEYSTORE_ALIAS || 'release' }}", WORKFLOW)
        self.assertIn(
            "SIGNING_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD || secrets.KEYSTORE_PASSWORD }}",
            WORKFLOW,
        )
        self.assertIn('-alias "$SIGNING_KEY_ALIAS"', WORKFLOW)
        self.assertIn('--ks-key-alias "$SIGNING_KEY_ALIAS"', WORKFLOW)
        self.assertIn("--ks-pass env:SIGNING_STORE_PASSWORD", WORKFLOW)
        self.assertIn("--key-pass env:SIGNING_KEY_PASSWORD", WORKFLOW)
        self.assertNotIn("--ks-key-alias release", WORKFLOW)
        self.assertNotIn("--key-pass env:KEYSTORE_PASSWORD", WORKFLOW)

    def test_pull_requests_have_one_stable_aggregate_check(self):
        instrumentation = WORKFLOW.split("\n  instrumentation:", 1)[1].split(
            "\n  instrumentation-gate:", 1
        )[0]
        instrumentation_gate = WORKFLOW.split("\n  instrumentation-gate:", 1)[1].split(
            "\n  pr-gate:", 1
        )[0]
        pull_request_gate = WORKFLOW.split("\n  pr-gate:", 1)[1].split(
            "\n  package:", 1
        )[0]
        package = WORKFLOW.split("\n  package:", 1)[1].split("\n  upgrade:", 1)[0]

        self.assertIn("connectedDebugAndroidTest", instrumentation)
        self.assertIn("name: instrumentation-gate", instrumentation_gate)
        self.assertIn("needs: [quality, release_scope, instrumentation]", instrumentation_gate)
        self.assertIn('test "$INSTRUMENTATION_RESULT" = success', instrumentation_gate)
        self.assertIn("name: pull-request-gate", pull_request_gate)
        self.assertIn(
            "needs: [quality, release_scope, instrumentation-gate]", pull_request_gate
        )
        self.assertIn('test "$INSTRUMENTATION_GATE_RESULT" = success', pull_request_gate)
        self.assertIn("needs: [quality, instrumentation-gate, release_scope]", package)


if __name__ == "__main__":
    unittest.main()
