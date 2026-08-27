import hashlib
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
ROOT_BUILD = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
APP_BUILD = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
GRADLE_PROPERTIES = (ROOT / "gradle.properties").read_text(encoding="utf-8")
GRADLE_WRAPPER = (
    ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
).read_text(encoding="utf-8")
GRADLE_WRAPPER_JAR = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
GRADLE_UNIX_LAUNCHER = (ROOT / "gradlew").read_text(encoding="utf-8")
GRADLE_WINDOWS_LAUNCHER = (ROOT / "gradlew.bat").read_text(encoding="utf-8")
BUILT_IN_KOTLIN_SMOKE = (
    ROOT
    / "app"
    / "src"
    / "test"
    / "kotlin"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "BuiltInKotlinSmokeTest.kt"
).read_text(encoding="utf-8")
DEBUG_MANIFEST = (ROOT / "app" / "src" / "debug" / "AndroidManifest.xml").read_text(
    encoding="utf-8"
)
MAIN_MANIFEST = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text(
    encoding="utf-8"
)
COMPOSE_SMOKE_HOST = (
    ROOT
    / "app"
    / "src"
    / "debug"
    / "kotlin"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "ComposeSmokeActivity.kt"
).read_text(encoding="utf-8")
COMPOSE_SMOKE_TEST = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "kotlin"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "ComposeSmokeInstrumentationTest.kt"
).read_text(encoding="utf-8")
DEBUG_PROGUARD = (ROOT / "app" / "proguard-debug.pro").read_text(encoding="utf-8")
RELEASE_PROGUARD = (ROOT / "app" / "proguard-release.pro").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "verify.yml").read_text(encoding="utf-8")
SOAK_WORKFLOW = (ROOT / ".github" / "workflows" / "instrumentation-soak.yml").read_text(
    encoding="utf-8"
)
INSTRUMENTATION_RUNNER = (ROOT / "scripts" / "ci" / "run-instrumentation.sh").read_text(
    encoding="utf-8"
)
PREVIEW_SDK_RUNNER = (
    ROOT / "scripts" / "ci" / "prepare-preview-sdk-tools.sh"
).read_text(encoding="utf-8")
UPGRADE_RUNNER = (ROOT / "scripts" / "ci" / "run-upgrade-test.sh").read_text(
    encoding="utf-8"
)
UPGRADE_INSTRUMENTATION = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "UpgradeProbeInstrumentation.java"
).read_text(encoding="utf-8")
UPGRADE_PROBE = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "UpgradePersistenceProbe.java"
).read_text(encoding="utf-8")
RELEASE_TOOL = (ROOT / "scripts" / "release" / "release_tool.py").read_text(
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
EDITOR_INTERACTION_TEST = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "kotlin"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "TaskEditorComposeInstrumentationTest.kt"
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
PRESENTATION_AWAITER = (
    ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "PresentationAwaiter.java"
).read_text(encoding="utf-8")
DEBUG_PRESENTATION_TRACE = (
    ROOT
    / "app"
    / "src"
    / "debug"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "PresentationTrace.java"
).read_text(encoding="utf-8")
RELEASE_PRESENTATION_TRACE = (
    ROOT
    / "app"
    / "src"
    / "release"
    / "java"
    / "de"
    / "thonktank"
    / "autosecretary"
    / "PresentationTrace.java"
).read_text(encoding="utf-8")


class WorkflowContractTest(unittest.TestCase):
    def test_phase_2a_build_foundation_is_exact_and_uses_built_in_kotlin(self):
        self.assertIn(
            'id("com.android.application") version "9.2.0" apply false',
            ROOT_BUILD,
        )
        self.assertIn("gradle-9.4.1-bin.zip", GRADLE_WRAPPER)
        self.assertEqual(
            "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c",
            hashlib.sha256(GRADLE_WRAPPER_JAR.read_bytes()).hexdigest(),
        )
        self.assertIn('-jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"', GRADLE_UNIX_LAUNCHER)
        self.assertIn(
            '-jar "%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar"',
            GRADLE_WINDOWS_LAUNCHER,
        )
        self.assertIn("compileSdk = 37", APP_BUILD)
        self.assertIn("minSdk = 26", APP_BUILD)
        self.assertIn("targetSdk = 35", APP_BUILD)
        self.assertIn("sourceCompatibility = JavaVersion.VERSION_17", APP_BUILD)
        self.assertIn("targetCompatibility = JavaVersion.VERSION_17", APP_BUILD)
        self.assertNotIn("org.jetbrains.kotlin.android", ROOT_BUILD + APP_BUILD)
        self.assertNotIn("android.builtInKotlin=false", GRADLE_PROPERTIES)
        self.assertIn("kotlinSourcesCompileThroughAgp", BUILT_IN_KOTLIN_SMOKE)

    def test_phase_2b_compose_smoke_host_is_exact_invisible_and_debug_only(self):
        self.assertIn(
            'id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false',
            ROOT_BUILD,
        )
        self.assertIn('id("org.jetbrains.kotlin.plugin.compose")', APP_BUILD)
        self.assertIn("compose = true", APP_BUILD)
        self.assertIn(
            'implementation(platform("androidx.compose:compose-bom:2026.08.00"))',
            APP_BUILD,
        )
        self.assertIn('implementation("androidx.compose.ui:ui")', APP_BUILD)
        self.assertIn('implementation("androidx.activity:activity:1.13.0")', APP_BUILD)
        self.assertIn(
            'implementation("androidx.activity:activity-compose:1.13.0")',
            APP_BUILD,
        )
        self.assertIn('implementation("androidx.room:room-ktx:2.8.4")', APP_BUILD)
        self.assertIn('implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")', APP_BUILD)
        self.assertIn('implementation("androidx.core:core:1.18.0")', APP_BUILD)
        self.assertNotIn('androidx.core:core:1.13.1', APP_BUILD)
        self.assertNotIn("androidx.compose.material", APP_BUILD + COMPOSE_SMOKE_HOST)
        self.assertIn("setContent { }", COMPOSE_SMOKE_HOST)
        self.assertNotIn("remember", COMPOSE_SMOKE_HOST)
        self.assertNotIn("mutableState", COMPOSE_SMOKE_HOST)
        self.assertIn('android:name=".ComposeSmokeActivity"', DEBUG_MANIFEST)
        self.assertIn('android:exported="false"', DEBUG_MANIFEST)
        self.assertNotIn("ComposeSmokeActivity", MAIN_MANIFEST)
        self.assertIn("composeView.hasComposition", COMPOSE_SMOKE_TEST)
        self.assertIn("FLAG_DIM_BEHIND", COMPOSE_SMOKE_TEST)
        self.assertIn(
            'test "$(stat -c%s app/build/outputs/apk/debug/app-debug.apk)" -lt 10485760',
            WORKFLOW,
        )

    def test_phase_5b_product_compose_keeps_both_apk_budgets_without_test_leakage(self):
        self.assertIn('implementation("androidx.compose.foundation:foundation")', APP_BUILD)
        self.assertIn('implementation("androidx.compose.animation:animation")', APP_BUILD)
        self.assertIn("isMinifyEnabled = true", APP_BUILD)
        release_build = APP_BUILD.split('getByName("release")', 1)[1].split(
            "testBuildType", 1
        )[0]
        self.assertIn("isShrinkResources = true", release_build)
        self.assertIn('"proguard-release.pro"', release_build)
        self.assertIn('create("instrumentation")', APP_BUILD)
        self.assertIn('testBuildType = "instrumentation"', APP_BUILD)
        self.assertIn('getByName("instrumentation").setRoot("src/debug")', APP_BUILD)
        instrumentation_build = APP_BUILD.split('create("instrumentation")', 1)[1].split(
            'getByName("release")', 1
        )[0]
        self.assertIn("isMinifyEnabled = false", instrumentation_build)
        self.assertNotIn("ui-test-manifest", APP_BUILD)
        self.assertIn("-keep class de.thonktank.autosecretary.** { *; }", DEBUG_PROGUARD)
        self.assertIn("-keepattributes SourceFile,LineNumberTable", DEBUG_PROGUARD)
        self.assertNotIn("-keep class kotlin.** { *; }", RELEASE_PROGUARD)
        self.assertIn("assembleInstrumentationAndroidTest", WORKFLOW)
        self.assertNotIn("assembleDebugAndroidTest", WORKFLOW)
        self.assertIn("testInstrumentationUnitTest", WORKFLOW)
        self.assertIn(
            "app/build/outputs/apk/androidTest/instrumentation/"
            "app-instrumentation-androidTest.apk",
            WORKFLOW,
        )
        self.assertIn("-PupgradeProbeRunner=true", WORKFLOW)
        self.assertIn('providers.gradleProperty("upgradeProbeRunner")', APP_BUILD)
        self.assertIn(
            '"de.thonktank.autosecretary.UpgradeProbeInstrumentation"', APP_BUILD
        )
        self.assertIn("extends Instrumentation", UPGRADE_INSTRUMENTATION)
        self.assertNotIn("androidx.", UPGRADE_INSTRUMENTATION + UPGRADE_PROBE)
        self.assertNotIn("org.junit", UPGRADE_INSTRUMENTATION + UPGRADE_PROBE)
        for product_api in (
            "AutoSecretaryApplication",
            "AppDatabase",
            "DatabaseContract",
            "data.local",
            "UiThemeMode",
            "CalendarPolicy",
        ):
            self.assertNotIn(product_api, UPGRADE_PROBE)
        self.assertIn("SQLiteDatabase.OPEN_READONLY", UPGRADE_PROBE)
        self.assertIn("TARGET_DATABASE_VERSION = 20", UPGRADE_PROBE)
        self.assertIn("SOURCE_DATABASE_VERSION = 8", UPGRADE_PROBE)
        self.assertIn("awaitDatabaseVersion(targetContext", UPGRADE_PROBE)
        self.assertIn("System.currentTimeMillis()", UPGRADE_PROBE)
        self.assertIn(".putLong(EXPECTED_LAST_CHECK, expectedLastCheck)", UPGRADE_PROBE)
        seed_method = UPGRADE_PROBE.split("static void seed(", 1)[1].split(
            "static void verify(", 1
        )[0]
        self.assertLess(
            seed_method.index("startMainActivity(targetContext, instrumentation)"),
            seed_method.index("awaitDatabaseVersion(targetContext"),
        )
        verify_method = UPGRADE_PROBE.split("static void verify(", 1)[1].split(
            "private static Activity startMainActivity", 1
        )[0]
        activity_start = verify_method.index("startMainActivity(context, instrumentation)")
        after_start = verify_method.index("verifyPreferencesAfterActivityStart(context")
        self.assertLess(activity_start, after_start)
        self.assertIn(
            'equal(expectedLastCheck, updates.getLong("last_update_check", -1L))',
            UPGRADE_PROBE,
        )
        for key in ("last_update_check", "postponed_update_code", "postponed_update_at"):
            self.assertIn(f'!ui.contains("{key}")', UPGRADE_PROBE)
        self.assertIn("OK (1 probe)", UPGRADE_INSTRUMENTATION)

    def test_phase_2c_sizes_api_37_and_release_install_paths_are_mandatory(self):
        quality = WORKFLOW.split("\n  quality:", 1)[1].split(
            "\n  instrumentation:", 1
        )[0]
        instrumentation = WORKFLOW.split("\n  instrumentation:", 1)[1].split(
            "\n  animation-instrumentation:", 1
        )[0]
        animation = WORKFLOW.split("\n  animation-instrumentation:", 1)[1].split(
            "\n  instrumentation-gate:", 1
        )[0]
        upgrade = WORKFLOW.split("\n  upgrade:", 1)[1].split("\n  publish:", 1)[0]

        self.assertIn(
            'test "$(stat -c%s app/build/outputs/apk/release/app-release-unsigned.apk)" -lt 8388608',
            quality,
        )
        self.assertIn(
            'test "$(du -cb app/src/main/res/font/*.ttf | tail -1 | cut -f1)" -lt 1677722',
            quality,
        )
        self.assertIn("MAX_APK_BYTES = 8 * 1024 * 1024", RELEASE_TOOL)
        for job in (instrumentation, animation, upgrade):
            with self.subTest(job=job[:40]):
                self.assertIn('api-level: "37.0"', job)
                self.assertIn("channel: canary", job)
                self.assertIn("if: matrix.channel != 'stable'", job)
                self.assertIn("./scripts/ci/prepare-preview-sdk-tools.sh", job)
                self.assertIn("channel: ${{ matrix.channel }}", job)
                self.assertIn("pre-emulator-launch-script: adb start-server", job)
        self.assertNotIn('cmdline-tools;latest', WORKFLOW)
        self.assertIn('sdkmanager" --install "cmdline-tools;latest"', PREVIEW_SDK_RUNNER)
        self.assertIn('test -x "$new_tools/bin/avdmanager"', PREVIEW_SDK_RUNNER)
        self.assertIn('"$current_tools/bin/avdmanager" list device', PREVIEW_SDK_RUNNER)
        self.assertIn('test "$tools_major" -ge 22', PREVIEW_SDK_RUNNER)

        fresh_install = UPGRADE_RUNNER.index('install_apk "$candidate_apk"')
        clean_remove = UPGRADE_RUNNER.index('adb uninstall "$package_name"')
        previous_install = UPGRADE_RUNNER.index('install_apk "$previous_apk"')
        upgrade_install = UPGRADE_RUNNER.index('install_apk "$candidate_apk" upgrade')
        self.assertLess(fresh_install, clean_remove)
        self.assertLess(clean_remove, previous_install)
        self.assertLess(previous_install, upgrade_install)
        self.assertEqual(2, UPGRADE_RUNNER.count("\nverify_installed_version\n"))
        self.assertEqual(2, UPGRADE_RUNNER.count("\nverify_package_absent\n"))
        self.assertEqual(2, UPGRADE_RUNNER.count("\nstart_main_activity\n"))
        self.assertIn('*"Status: ok"*', UPGRADE_RUNNER)
        self.assertIn('pm list packages "$package_name"', UPGRADE_RUNNER)
        self.assertNotIn('pm path "$package_name"', UPGRADE_RUNNER)

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
        self.assertIn(
            "run: ./scripts/ci/prepare-preview-sdk-tools.sh",
            animation_instrumentation,
        )
        self.assertIn("pre-emulator-launch-script: adb start-server", animation_instrumentation)
        self.assertIn("disable-animations: false", animation_instrumentation)
        self.assertIn('INSTRUMENTATION_ANIMATION_SCALE: "1.0"', animation_instrumentation)
        self.assertIn(
            'INSTRUMENTATION_PREPARE_INTERACTION_DEVICE: "true"',
            animation_instrumentation,
        )
        self.assertIn("TaskEditorComposeInstrumentationTest", animation_instrumentation)
        self.assertIn("TaskEditorComposeApi37InstrumentationTest", animation_instrumentation)
        self.assertIn("AllTasksComposeInstrumentationTest", animation_instrumentation)
        self.assertIn("AllTasksComposeApi37InstrumentationTest", animation_instrumentation)
        self.assertNotIn("AllTasksInteractionTest", animation_instrumentation)
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
        self.assertIn("connectedInstrumentationAndroidTest", INSTRUMENTATION_RUNNER)
        self.assertIn("status=$?", INSTRUMENTATION_RUNNER)
        self.assertIn('exit "$status"', INSTRUMENTATION_RUNNER)
        self.assertIn("hide_error_dialogs", INSTRUMENTATION_RUNNER)
        self.assertIn("interaction-settings.txt", INSTRUMENTATION_RUNNER)
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

    def test_critical_interaction_tests_use_signals_instead_of_polling(self):
        self.assertIn("compose.waitUntil", EDITOR_INTERACTION_TEST)
        self.assertNotIn("SystemClock.sleep", EDITOR_INTERACTION_TEST)
        self.assertNotIn("UI_POLL_MILLIS", EDITOR_INTERACTION_TEST)
        self.assertIn("PresentationAwaiter.await", TODAY_GESTURE_TEST)
        self.assertNotIn("SystemClock.sleep", TODAY_GESTURE_TEST)
        self.assertNotIn("UI_POLL_MILLIS", TODAY_GESTURE_TEST)

        self.assertIn("CountDownLatch", PRESENTATION_AWAITER)
        self.assertIn("TIMEOUT_MILLIS", PRESENTATION_AWAITER)
        self.assertNotIn("SystemClock.sleep", PRESENTATION_AWAITER)
        self.assertIn(
            "recreationKeepsTheAuthoritativeDraftAndCurrentPage", EDITOR_INTERACTION_TEST
        )
        self.assertIn(
            "focusedInputAndPageScrollSurviveRecreation",
            EDITOR_INTERACTION_TEST,
        )

    def test_presentation_trace_is_bounded_in_debug_and_disabled_in_release(self):
        self.assertIn("CAPACITY = 256", DEBUG_PRESENTATION_TRACE)
        self.assertIn("boolean enabled() { return true; }", DEBUG_PRESENTATION_TRACE)
        self.assertIn("boolean enabled() { return false; }", RELEASE_PRESENTATION_TRACE)
        self.assertNotIn("ArrayDeque", RELEASE_PRESENTATION_TRACE)
        self.assertNotIn("CopyOnWriteArrayList", RELEASE_PRESENTATION_TRACE)


if __name__ == "__main__":
    unittest.main()
