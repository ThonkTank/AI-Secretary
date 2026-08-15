#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "Usage: $0 PREVIOUS_APK CANDIDATE_APK TEST_APK PACKAGE CANDIDATE_VERSION" >&2
  exit 2
fi

previous_apk=$1
candidate_apk=$2
test_apk=$3
package_name=$4
candidate_version=$5
runner="${package_name}.test/androidx.test.runner.AndroidJUnitRunner"
test_class="${package_name}.UpgradePersistenceTest"

run_probe() {
  local phase=$1
  local method=$2
  local output
  output=$(adb shell am instrument -w -r -e upgradePhase "$phase" \
    -e class "${test_class}#${method}" "$runner")
  printf '%s\n' "$output"
  case "$output" in
    *"OK (1 test)"*) ;;
    *) exit 1 ;;
  esac
}

for artifact in "$previous_apk" "$candidate_apk" "$test_apk"; do
  test -f "$artifact"
done

adb install "$previous_apk"
adb shell am start -W -n "${package_name}/.MainActivity"
adb shell am force-stop "$package_name"
adb install "$test_apk"
run_probe seed seedPreviousVersion

upgrade_result=$(adb install -r "$candidate_apk")
case "$upgrade_result" in
  *Success*) ;;
  *) echo "$upgrade_result" >&2; exit 1 ;;
esac

installed_version=$(adb shell dumpsys package "$package_name" \
  | sed -n 's/^[[:space:]]*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
test "$installed_version" = "$candidate_version"
run_probe verify currentVersionStartsAndReadsPreviousData
adb shell am force-stop "$package_name"
