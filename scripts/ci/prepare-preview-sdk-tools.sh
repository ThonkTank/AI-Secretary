#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
current_tools="$sdk_root/cmdline-tools/latest"
new_tools="$sdk_root/cmdline-tools/latest-2"
backup_tools="$RUNNER_TEMP/android-cmdline-tools-before-preview"

test -n "$sdk_root"
test -x "$current_tools/bin/sdkmanager"
test ! -e "$new_tools"
test ! -e "$backup_tools"

# sdkmanager may close stdin before yes exits. Verify the installed tool instead of relying on
# the pipeline status, but retain it in the log for diagnosing preview-image setup failures.
set +e
set +o pipefail
yes | "$current_tools/bin/sdkmanager" --install "cmdline-tools;latest" >/dev/null
install_status=${PIPESTATUS[1]}
set -o pipefail
set -e
test -x "$new_tools/bin/avdmanager"
echo "SDK manager side-install exit status: $install_status"

mv "$current_tools" "$backup_tools"
mv "$new_tools" "$current_tools"
"$current_tools/bin/avdmanager" list device >/dev/null
test -f "$current_tools/source.properties"
tools_version=$(sed -n 's/^Pkg.Revision=//p' \
  "$current_tools/source.properties" | head -n 1)
tools_major=${tools_version%%.*}
test -n "$tools_version"
test "$tools_major" -ge 22
echo "Android SDK command-line tools $tools_version"
