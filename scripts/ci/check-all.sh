#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
./gradlew testInstrumentationUnitTest lintDebug assembleDebug assembleInstrumentation \
  assembleInstrumentationAndroidTest assembleRelease
./scripts/ci/verify-instrumentation-identity.sh
test "$(stat -c%s app/build/outputs/apk/debug/app-debug.apk)" -lt 10485760
test "$(stat -c%s app/build/outputs/apk/release/app-release-unsigned.apk)" -lt 8388608
test "$(du -cb app/src/main/res/font/*.ttf | tail -1 | cut -f1)" -lt 1677722
