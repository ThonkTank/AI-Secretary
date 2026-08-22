#!/usr/bin/env bash
set -uo pipefail

api_level="${INSTRUMENTATION_API_LEVEL:-unknown}"
attempt="${INSTRUMENTATION_ATTEMPT:-1}"
report_root="${INSTRUMENTATION_REPORT_ROOT:-build/reports/instrumentation}"
report_dir="$report_root/api-$api_level/attempt-$attempt"
test_class="${INSTRUMENTATION_TEST_CLASS:-}"
gradle_executable="${INSTRUMENTATION_GRADLE_EXECUTABLE:-./gradlew}"

gradle_arguments=(connectedDebugAndroidTest --stacktrace)
if [ -n "$test_class" ]; then
  gradle_arguments+=("-Pandroid.testInstrumentationRunnerArguments.class=$test_class")
fi
if [ "${INSTRUMENTATION_RERUN_TASKS:-false}" = true ]; then
  gradle_arguments+=(--rerun-tasks)
fi

"$gradle_executable" "${gradle_arguments[@]}"
status=$?

if [ "$status" -ne 0 ]; then
  mkdir -p "$report_dir"
  printf 'api_level=%s\nattempt=%s\ntest_class=%s\ngradle_exit_code=%s\n' \
    "$api_level" "$attempt" "${test_class:-all}" "$status" > "$report_dir/run-context.txt"
  adb devices -l > "$report_dir/adb-devices.txt" 2>&1 || true
  adb exec-out screencap -p > "$report_dir/screenshot.png" \
    2> "$report_dir/screenshot-error.txt" || true
  adb shell uiautomator dump /sdcard/auto-secretary-window.xml \
    > "$report_dir/ui-hierarchy-command.txt" 2>&1 || true
  adb pull /sdcard/auto-secretary-window.xml "$report_dir/ui-hierarchy.xml" \
    > "$report_dir/ui-hierarchy-pull.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$report_dir/logcat.txt" 2>&1 || true
  adb shell getevent -lp > "$report_dir/input-devices.txt" 2>&1 || true
  adb shell dumpsys input > "$report_dir/dumpsys-input.txt" 2>&1 || true
  adb shell dumpsys display > "$report_dir/dumpsys-display.txt" 2>&1 || true
  adb shell dumpsys window > "$report_dir/dumpsys-window.txt" 2>&1 || true
  adb shell getprop > "$report_dir/device-properties.txt" 2>&1 || true
fi

exit "$status"
