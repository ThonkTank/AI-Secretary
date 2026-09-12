#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 6 ]; then
  echo "Usage: $0 SOURCE_APK CANDIDATE_APK TEST_APK PACKAGE CANDIDATE_VERSION FIXTURE_ID" >&2
  exit 2
fi

source_apk=$1
candidate_apk=$2
test_apk=$3
package_name=$4
candidate_version=$5
fixture_id=$6
runner="${package_name}.test/${package_name}.UpgradeProbeInstrumentation"

if [[ ! "$fixture_id" =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]]; then
  echo "Invalid fixture ID: $fixture_id" >&2
  exit 2
fi

install_apk() {
  local apk=$1
  local mode=${2:-fresh}
  local output
  if [ "$mode" = upgrade ]; then
    output=$(adb install -r "$apk")
  else
    output=$(adb install "$apk")
  fi
  printf '%s\n' "$output"
  case "$output" in
    *Success*) ;;
    *) exit 1 ;;
  esac
}

verify_installed_version() {
  local installed_version
  installed_version=$(adb shell dumpsys package "$package_name" \
    | sed -n 's/^[[:space:]]*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
  test "$installed_version" = "$candidate_version"
}

verify_package_absent() {
  local installed_packages
  installed_packages=$(adb shell pm list packages "$package_name" | tr -d '\r')
  if printf '%s\n' "$installed_packages" | grep -Fxq "package:$package_name"; then
    echo "Package is still installed: $package_name" >&2
    exit 1
  fi
}

start_main_activity() {
  local output
  output=$(adb shell am start -W -n "${package_name}/.MainActivity")
  printf '%s\n' "$output"
  case "$output" in
    *"Status: ok"*) ;;
    *) exit 1 ;;
  esac
}

run_probe() {
  local phase=$1
  local output
  local status
  set +e
  output=$(adb shell am instrument -w -r -e upgradePhase "$phase" \
    -e upgradeFixture "$fixture_id" "$runner" 2>&1)
  status=$?
  set -e
  printf '%s\n' "$output"
  if [ "$status" -eq 0 ] && [[ "$output" == *"OK (1 probe)"* ]]; then
    return
  fi
  echo "Upgrade probe '$phase' failed; recent device log follows" >&2
  adb logcat -d -v threadtime 2>&1 | tail -400 >&2 || true
  return 1
}

run_diagnostic() {
  local expectation=$1
  local status
  set +e
  python3 scripts/release/diagnostic_probe.py --package "$package_name"
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then return; fi
  # The pinned preceding APK may predate the early contract. That must be an explicit
  # unsupported result, not a generic process crash. Every new candidate must support it.
  if [ "$expectation" = source ] && [ "$status" -eq 4 ]; then return; fi
  return "$status"
}

for artifact in "$source_apk" "$candidate_apk" "$test_apk"; do
  test -f "$artifact"
done

# First prove that the exact signed candidate installs and starts on a clean device. Remove it
# before exercising the independent signed-source-to-candidate upgrade path below.
verify_package_absent
install_apk "$candidate_apk"
start_main_activity
verify_installed_version
adb shell am force-stop "$package_name"
uninstall_result=$(adb uninstall "$package_name")
case "$uninstall_result" in
  *Success*) ;;
  *) echo "$uninstall_result" >&2; exit 1 ;;
esac
verify_package_absent

install_apk "$source_apk"
start_main_activity
adb shell am force-stop "$package_name"
install_apk "$test_apk"
run_probe seed
if [ "${UPGRADE_DIAGNOSTIC_CONTRACT:-false}" = true ]; then run_diagnostic source; fi

install_apk "$candidate_apk" upgrade
verify_installed_version
if [ "${UPGRADE_DIAGNOSTIC_CONTRACT:-false}" = true ]; then run_diagnostic candidate; fi
run_probe verify
adb shell am force-stop "$package_name"
