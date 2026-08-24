#!/usr/bin/env bash
set -uo pipefail

api_level="${INSTRUMENTATION_API_LEVEL:-unknown}"
attempt="${INSTRUMENTATION_ATTEMPT:-1}"
report_root="${INSTRUMENTATION_REPORT_ROOT:-build/reports/instrumentation}"
report_dir="$report_root/api-$api_level/attempt-$attempt"
test_class="${INSTRUMENTATION_TEST_CLASS:-}"
gradle_executable="${INSTRUMENTATION_GRADLE_EXECUTABLE:-./gradlew}"
animation_scale="${INSTRUMENTATION_ANIMATION_SCALE:-}"
prepare_interaction_device="${INSTRUMENTATION_PREPARE_INTERACTION_DEVICE:-false}"
animation_settings=(window_animation_scale transition_animation_scale animator_duration_scale)

gradle_arguments=(connectedDebugAndroidTest --stacktrace)
if [ -n "$test_class" ]; then
  gradle_arguments+=("-Pandroid.testInstrumentationRunnerArguments.class=$test_class")
fi
if [ "${INSTRUMENTATION_RERUN_TASKS:-false}" = true ]; then
  gradle_arguments+=(--rerun-tasks)
fi

status=0
if [ "$prepare_interaction_device" = true ]; then
  adb shell input keyevent KEYCODE_WAKEUP || status=43
  adb shell wm dismiss-keyguard || status=43
  adb shell input keyevent 82 || status=43
fi
if [ -n "$animation_scale" ]; then
  for setting in "${animation_settings[@]}"; do
    adb shell settings put global "$setting" "$animation_scale" || status=41
  done
  for setting in "${animation_settings[@]}"; do
    actual=$(adb shell settings get global "$setting") || status=41
    if [ "$actual" != "$animation_scale" ]; then
      printf 'Expected %s=%s, got %s\n' "$setting" "$animation_scale" "$actual" >&2
      status=42
    fi
  done
fi

if [ "$status" -eq 0 ]; then
  "$gradle_executable" "${gradle_arguments[@]}"
  status=$?
fi

if [ "$status" -ne 0 ]; then
  mkdir -p "$report_dir"
  printf 'api_level=%s\nattempt=%s\ntest_class=%s\nanimation_scale=%s\nprepare_interaction_device=%s\ngradle_exit_code=%s\nexit_code=%s\n' \
    "$api_level" "$attempt" "${test_class:-all}" "${animation_scale:-unchanged}" \
    "$prepare_interaction_device" "$status" "$status" \
    > "$report_dir/run-context.txt"
  for setting in "${animation_settings[@]}"; do
    printf '%s=%s\n' "$setting" "$(adb shell settings get global "$setting" 2>/dev/null \
      || printf unreadable)"
  done > "$report_dir/animation-scales.txt"
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
