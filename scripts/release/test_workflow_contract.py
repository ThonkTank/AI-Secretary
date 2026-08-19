import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github" / "workflows" / "verify.yml").read_text(encoding="utf-8")


class WorkflowContractTest(unittest.TestCase):
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
            "\n  pr-gate:", 1
        )[0]
        pull_request_gate = WORKFLOW.split("\n  pr-gate:", 1)[1].split(
            "\n  package:", 1
        )[0]
        package = WORKFLOW.split("\n  package:", 1)[1].split("\n  upgrade:", 1)[0]

        self.assertIn("if: github.event_name != 'pull_request'", instrumentation)
        self.assertIn("name: pull-request-gate", pull_request_gate)
        self.assertIn("needs: [quality]", pull_request_gate)
        self.assertIn('test "$QUALITY_RESULT" = success', pull_request_gate)
        self.assertNotIn("INSTRUMENTATION_RESULT", pull_request_gate)
        self.assertIn("needs: [quality, instrumentation]", package)


if __name__ == "__main__":
    unittest.main()
