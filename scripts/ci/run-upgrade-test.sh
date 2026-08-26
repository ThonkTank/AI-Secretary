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
runner="${package_name}.test/${package_name}.UpgradeProbeInstrumentation"

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
  output=$(adb shell am instrument -w -r -e upgradePhase "$phase" "$runner" 2>&1)
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

for artifact in "$previous_apk" "$candidate_apk" "$test_apk"; do
  test -f "$artifact"
done

# First prove that the exact signed candidate installs and starts on a clean device. Remove it
# before exercising the independent previous-production-to-candidate upgrade path below.
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

install_apk "$previous_apk"
start_main_activity
adb shell am force-stop "$package_name"
install_apk "$test_apk"
run_probe seed

install_apk "$candidate_apk" upgrade
verify_installed_version
run_probe verify
adb shell am force-stop "$package_name"
