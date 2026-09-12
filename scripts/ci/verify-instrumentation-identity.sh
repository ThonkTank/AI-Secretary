#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

build_tools=$(sed -n 's/^androidBuildTools=//p' release/release.properties)
aapt_bin="${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}/build-tools/$build_tools/aapt"
app_apk=app/build/outputs/apk/instrumentation/app-instrumentation.apk
test_apk=app/build/outputs/apk/androidTest/instrumentation/app-instrumentation-androidTest.apk

app_badging=$($aapt_bin dump badging "$app_apk")
test_badging=$($aapt_bin dump badging "$test_apk")
test_manifest=$($aapt_bin dump xmltree "$test_apk" AndroidManifest.xml)

case "$app_badging" in
  *"package: name='de.thonktank.autosecretary.test'"*"application-label:'Auto Secretary Test'"*) ;;
  *) echo "Regular instrumentation app is not isolated: $app_badging" >&2; exit 1 ;;
esac
case "$test_badging" in
  *"package: name='de.thonktank.autosecretary.test.test'"*) ;;
  *) echo "Unexpected regular instrumentation test package: $test_badging" >&2; exit 1 ;;
esac
python3 scripts/ci/instrumentation_manifest.py --mode regular <<< "$test_manifest"
