import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github" / "workflows" / "verify.yml").read_text(encoding="utf-8")
SOAK_WORKFLOW = (ROOT / ".github" / "workflows" / "instrumentation-soak.yml").read_text(
    encoding="utf-8"
)
INSTRUMENTATION_RUNNER = (ROOT / "scripts" / "ci" / "run-instrumentation.sh").read_text(
    encoding="utf-8"
)
SOAK_RUNNER = (ROOT / "scripts" / "ci" / "run-instrumentation-soak.sh").read_text(
    encoding="utf-8"
)
TODAY_GESTURE_TEST = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "ui"
    / "today"
    / "TodayInteractionInstrumentationTest.java"
).read_text(encoding="utf-8")
TOUCH_DRIVER = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "ui"
    / "today"
    / "TouchGestureDriver.java"
).read_text(encoding="utf-8")


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
        self.assertIn(
            'INSTRUMENTATION_PREPARE_INTERACTION_DEVICE: "true"', instrumentation
        )
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
        animation_instrumentation = WORKFLOW.split(
            "\n  animation-instrumentation:", 1
        )[1].split("\n  instrumentation-gate:", 1)[0]
        instrumentation_gate = WORKFLOW.split("\n  instrumentation-gate:", 1)[1].split(
            "\n  pr-gate:", 1
        )[0]
        pull_request_gate = WORKFLOW.split("\n  pr-gate:", 1)[1].split(
            "\n  package:", 1
        )[0]
        package = WORKFLOW.split("\n  package:", 1)[1].split("\n  upgrade:", 1)[0]

        self.assertIn("./scripts/ci/run-instrumentation.sh", instrumentation)
        self.assertIn("name: instrumentation-gate", instrumentation_gate)
        self.assertIn("api-level: 26", animation_instrumentation)
        self.assertIn("api-level: 35", animation_instrumentation)
        self.assertIn('api-level: "37.0"', animation_instrumentation)
        self.assertIn("channel: canary", animation_instrumentation)
        self.assertIn(
            "name: Update SDK tools for minor-versioned preview packages",
            animation_instrumentation,
        )
        self.assertIn("if: matrix.channel != 'stable'", animation_instrumentation)
        self.assertIn('cmdline-tools;latest', animation_instrumentation)
        self.assertIn('cmdline-tools/latest-2', animation_instrumentation)
        self.assertIn('test -x "$NEW_TOOLS/bin/avdmanager"', animation_instrumentation)
        self.assertIn('"$CURRENT_TOOLS/bin/avdmanager" list device', animation_instrumentation)
        self.assertIn('"$CURRENT_TOOLS/source.properties"', animation_instrumentation)
        self.assertIn('test "$TOOLS_MAJOR" -ge 22', animation_instrumentation)
        self.assertIn("pre-emulator-launch-script: adb start-server", animation_instrumentation)
        self.assertIn("disable-animations: false", animation_instrumentation)
        self.assertIn('INSTRUMENTATION_ANIMATION_SCALE: "1.0"', animation_instrumentation)
        self.assertIn(
            'INSTRUMENTATION_PREPARE_INTERACTION_DEVICE: "true"',
            animation_instrumentation,
        )
        self.assertIn("TaskEditorInteractionInstrumentationTest", animation_instrumentation)
        self.assertIn("AllTasksInteractionTest", animation_instrumentation)
        self.assertIn("TodayInteractionInstrumentationTest", animation_instrumentation)
        self.assertNotIn("INSTRUMENTATION_RERUN_TASKS", animation_instrumentation)
        self.assertIn(
            "needs: [quality, release_scope, instrumentation, animation-instrumentation]",
            instrumentation_gate,
        )
        self.assertIn('test "$INSTRUMENTATION_RESULT" = success', instrumentation_gate)
        self.assertIn(
            'test "$ANIMATION_INSTRUMENTATION_RESULT" = success', instrumentation_gate
        )
        self.assertIn("name: pull-request-gate", pull_request_gate)
        self.assertIn(
            "needs: [quality, release_scope, instrumentation-gate]", pull_request_gate
        )
        self.assertIn('test "$INSTRUMENTATION_GATE_RESULT" = success', pull_request_gate)
        self.assertIn("needs: [quality, instrumentation-gate, release_scope]", package)

    def test_instrumentation_failures_are_captured_before_the_emulator_stops(self):
        instrumentation = WORKFLOW.split("\n  instrumentation:", 1)[1].split(
            "\n  instrumentation-gate:", 1
        )[0]

        self.assertIn("INSTRUMENTATION_API_LEVEL: ${{ matrix.api-level }}", instrumentation)
        self.assertIn("if: failure()", instrumentation)
        self.assertIn("build/reports/instrumentation/", instrumentation)
        self.assertIn("androidTests/connected/", instrumentation)
        self.assertIn("./gradlew", INSTRUMENTATION_RUNNER)
        self.assertIn("connectedDebugAndroidTest", INSTRUMENTATION_RUNNER)
        self.assertIn("status=$?", INSTRUMENTATION_RUNNER)
        self.assertIn('exit "$status"', INSTRUMENTATION_RUNNER)
        for diagnostic in (
            "screencap -p",
            "uiautomator dump",
            "logcat -d",
            "getevent -lp",
            "dumpsys input",
            "dumpsys display",
        ):
            with self.subTest(diagnostic=diagnostic):
                self.assertIn(diagnostic, INSTRUMENTATION_RUNNER)

    def test_manual_soak_runs_five_clean_today_gesture_suites_per_api(self):
        self.assertIn("workflow_dispatch:", SOAK_WORKFLOW)
        self.assertIn("api-level: 26", SOAK_WORKFLOW)
        self.assertIn("api-level: 35", SOAK_WORKFLOW)
        self.assertIn("SOAK_REPETITIONS: 5", SOAK_WORKFLOW)
        self.assertIn("./scripts/ci/run-instrumentation-soak.sh", SOAK_WORKFLOW)
        self.assertIn("if: failure()", SOAK_WORKFLOW)
        self.assertIn("TodayInteractionInstrumentationTest", SOAK_RUNNER)
        self.assertIn('SOAK_REPETITIONS:-5', SOAK_RUNNER)
        self.assertIn("adb uninstall de.thonktank.autosecretary.test", SOAK_RUNNER)
        self.assertIn("adb uninstall de.thonktank.autosecretary", SOAK_RUNNER)
        self.assertIn("INSTRUMENTATION_RERUN_TASKS=true", SOAK_RUNNER)

    def test_today_gesture_failures_are_isolated_and_owned_by_one_driver(self):
        for method in (
            "longPressStartsReorder",
            "dragPreviewsAndDropPersistsReorder",
            "holdingAtBottomEdgeScrolls",
            "accessibilityReorderPersists",
            "recreationCancelsActiveReorder",
        ):
            with self.subTest(method=method):
                self.assertIn("@Test public void " + method, TODAY_GESTURE_TEST)
        self.assertIn("class TouchGestureDriver", TOUCH_DRIVER)
        self.assertIn("TOOL_TYPE_FINGER", TOUCH_DRIVER)
        self.assertIn("touchDeviceId", TOUCH_DRIVER)
        self.assertIn("displayId", TOUCH_DRIVER)
        self.assertIn("downTime", TOUCH_DRIVER)
        self.assertIn("actions=", TODAY_GESTURE_TEST)
        self.assertIn("scrollDistance=", TODAY_GESTURE_TEST)


if __name__ == "__main__":
    unittest.main()
