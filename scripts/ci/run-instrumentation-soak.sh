#!/usr/bin/env bash
set -euo pipefail

repetitions="${SOAK_REPETITIONS:-5}"
test_class="de.thonktank.autosecretary.ui.today.TodayInteractionInstrumentationTest"

for attempt in $(seq 1 "$repetitions"); do
  echo "Today gesture soak attempt $attempt/$repetitions on API ${INSTRUMENTATION_API_LEVEL:-unknown}"
  adb uninstall de.thonktank.autosecretary.test >/dev/null 2>&1 || true
  adb uninstall de.thonktank.autosecretary >/dev/null 2>&1 || true
  adb logcat -c || true
  INSTRUMENTATION_ATTEMPT="$attempt" \
  INSTRUMENTATION_TEST_CLASS="$test_class" \
  INSTRUMENTATION_RERUN_TASKS=true \
    ./scripts/ci/run-instrumentation.sh
done
