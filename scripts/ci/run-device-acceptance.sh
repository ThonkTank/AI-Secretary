#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 RELEASE_TAG" >&2
  exit 2
fi

tag=$1
report_root="${DEVICE_ACCEPTANCE_REPORT_ROOT:-build/reports/device-acceptance}"
report_dir="$report_root/$tag"
properties="${DEVICE_ACCEPTANCE_RELEASE_PROPERTIES:-release/release.properties}"
local_release_dir="${DEVICE_ACCEPTANCE_RELEASE_DIR:-}"
local_previous_metadata="${DEVICE_ACCEPTANCE_PREVIOUS_METADATA:-}"
metadata_asset=$(sed -n 's/^metadataAsset=//p' "$properties")
apk_asset=$(sed -n 's/^apkAsset=//p' "$properties")
package_expected=$(sed -n 's/^packageName=//p' "$properties")
tag_prefix=$(sed -n 's/^tagPrefix=//p' "$properties")
repository_owner=$(sed -n 's/^repositoryOwner=//p' "$properties")
repository_name=$(sed -n 's/^repositoryName=//p' "$properties")

for command in adb jq sha256sum sed; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 2
  }
done

mkdir -p "$report_dir"
serial=""
pre_code=""
pre_name=""
post_code=""
post_name=""
version_code=""
version_name=""
package_name=""
apk_sha=""
previous_code=""
previous_name=""
manual_accepted=false

write_report() {
  local status=$1
  local reason=$2
  jq -n \
    --arg status "$status" --arg reason "$reason" --arg tag "$tag" \
    --arg serial "$serial" --arg packageName "$package_name" \
    --arg releaseVersionCode "$version_code" --arg releaseVersionName "$version_name" \
    --arg previousVersionCode "$previous_code" --arg previousVersionName "$previous_name" \
    --arg installedBeforeCode "$pre_code" --arg installedBeforeName "$pre_name" \
    --arg installedAfterCode "$post_code" --arg installedAfterName "$post_name" \
    --arg apkSha256 "$apk_sha" --arg acceptedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson manualAccepted "$manual_accepted" \
    '{schemaVersion:1,status:$status,reason:$reason,releaseTag:$tag,deviceSerial:$serial,
      packageName:$packageName,
      release:{versionCode:$releaseVersionCode,versionName:$releaseVersionName,sha256:$apkSha256},
      previousProduction:{versionCode:$previousVersionCode,versionName:$previousVersionName},
      installedBefore:{versionCode:$installedBeforeCode,versionName:$installedBeforeName},
      installedAfter:{versionCode:$installedAfterCode,versionName:$installedAfterName},
      manualChecks:{dataPreserved:$manualAccepted,allTasksLayout:$manualAccepted,
        searchAndFilters:$manualAccepted,cardAndArchive:$manualAccepted,
        sortAndDrag:$manualAccepted,restartStateLifetime:$manualAccepted},
      recordedAt:$acceptedAt}' > "$report_dir/report.json"
}

stop() {
  local status=$1
  local reason=$2
  write_report "$status" "$reason"
  echo "$reason" >&2
  exit 1
}

temporary=""
cleanup() {
  if [ -n "$temporary" ] && [ -d "$temporary" ]; then rm -rf "$temporary"; fi
}
trap cleanup EXIT

if [ -n "$local_release_dir" ]; then
  metadata="$local_release_dir/$metadata_asset"
  apk="$local_release_dir/$apk_asset"
  previous_metadata="$local_previous_metadata"
else
  command -v gh >/dev/null || stop failed "Missing required command: gh"
  temporary=$(mktemp -d)
  mkdir -p "$temporary/current" "$temporary/previous"
  gh release download "$tag" --repo "$repository_owner/$repository_name" \
    --dir "$temporary/current" --pattern "$metadata_asset" --pattern "$apk_asset"
  metadata="$temporary/current/$metadata_asset"
  apk="$temporary/current/$apk_asset"
fi

test -f "$metadata" || stop failed "Release metadata is missing"
test -f "$apk" || stop failed "Release APK is missing"
version_code=$(jq -r '.versionCode // empty' "$metadata")
version_name=$(jq -r '.versionName // empty' "$metadata")
package_name=$(jq -r '.packageName // empty' "$metadata")
expected_sha=$(jq -r '.sha256 // empty' "$metadata")
expected_size=$(jq -r '.apkSizeBytes // empty' "$metadata")
commit_sha=$(jq -r '.commitSha // empty' "$metadata")
test "$(jq -r '.schemaVersion // empty' "$metadata")" = 1 \
  || stop failed "Unsupported release metadata schema"
test -n "$version_code" && test -n "$version_name" && test -n "$commit_sha" \
  || stop failed "Release metadata is incomplete"
test "$package_name" = "$package_expected" \
  || stop failed "Release metadata names an unexpected package"
test "$tag" = "$tag_prefix$version_code" \
  || stop failed "Release tag and versionCode disagree"
apk_sha=$(sha256sum "$apk" | awk '{print $1}')
test "$apk_sha" = "$expected_sha" || stop failed "Release APK hash mismatch"
test "$(stat -c%s "$apk")" = "$expected_size" || stop failed "Release APK size mismatch"

if [ -z "$local_release_dir" ]; then
  release_commit=$(gh release view "$tag" --repo "$repository_owner/$repository_name" \
    --json targetCommitish --jq .targetCommitish)
  test "$release_commit" = "$commit_sha" \
    || stop failed "Release target and metadata commit disagree"
  releases="$temporary/releases.json"
  gh api --paginate "repos/$repository_owner/$repository_name/releases?per_page=100" \
    | jq -s 'add' > "$releases"
  previous_tag=$(jq -r --arg prefix "$tag_prefix" --argjson current "$version_code" '
    [.[] | select(.draft == false) | .tag_name as $tag
      | (try ($tag | ltrimstr($prefix) | tonumber) catch null) as $code
      | select($tag | startswith($prefix)) | select($code != null and $code < $current)
      | {tag:$tag, code:$code}] | if length == 0 then "" else max_by(.code).tag end' \
    "$releases")
  test -n "$previous_tag" || stop failed "No previous production release found"
  gh release download "$previous_tag" --repo "$repository_owner/$repository_name" \
    --dir "$temporary/previous" --pattern "$metadata_asset"
  previous_metadata="$temporary/previous/$metadata_asset"
fi

test -f "$previous_metadata" || stop failed "Previous release metadata is missing"
previous_code=$(jq -r '.versionCode // empty' "$previous_metadata")
previous_name=$(jq -r '.versionName // empty' "$previous_metadata")
test "$(jq -r '.packageName // empty' "$previous_metadata")" = "$package_name" \
  || stop failed "Previous release package differs"
test -n "$previous_code" && test "$previous_code" -lt "$version_code" \
  || stop failed "Previous release version is invalid"

adb devices -l > "$report_dir/adb-devices.txt"
mapfile -t devices < <(adb devices | awk 'NR > 1 && NF >= 2 {print $1 " " $2}')
if [ "${#devices[@]}" -ne 1 ]; then
  stop pending "Expected exactly one connected ADB device"
fi
read -r serial device_state <<< "${devices[0]}"
test "$device_state" = device || stop pending "The only ADB device is not authorized"

installed_version() {
  local dump
  dump=$(adb -s "$serial" shell dumpsys package "$package_name")
  printf '%s\n' "$dump" | sed -n 's/^[[:space:]]*versionCode=\([0-9]*\).*/\1/p' \
    | head -n 1
  printf '%s\n' "$dump" | sed -n 's/^[[:space:]]*versionName=\(.*\)/\1/p' | head -n 1
}

mapfile -t before < <(installed_version)
pre_code="${before[0]:-}"
pre_name="${before[1]:-}"
test "$pre_code" = "$previous_code" && test "$pre_name" = "$previous_name" \
  || stop pending "Device is not on the previous production version $previous_name"

adb -s "$serial" shell am start -W -n "$package_name/.MainActivity" \
  > "$report_dir/launch-before-update.txt"
echo "Bitte jetzt auf dem Gerät den In-App-Updater öffnen und auf $version_name aktualisieren."
echo "Nicht per adb installieren. Nach erfolgreichem Neustart ENTER drücken."
read -r _ || stop pending "Acceptance paused before the in-app update"

mapfile -t after < <(installed_version)
post_code="${after[0]:-}"
post_name="${after[1]:-}"
test "$post_code" = "$version_code" && test "$post_name" = "$version_name" \
  || stop failed "Installed version does not match the published release"

cat >&2 <<'CHECKLIST'
Bitte auf dem Gerät prüfen und den Alles-Tab für den Screenshot geöffnet lassen:
- vorhandene Daten sind erhalten
- Suche, Filter, Kartenexpansion und Archivansicht funktionieren
- Sortier-/Dragmodus funktioniert
- nach Neustart bleibt der Filterbereich erhalten; Menü und Drag sind geschlossen
Danach exakt ACCEPTED eingeben.
CHECKLIST
read -r confirmation || stop pending "Acceptance paused before manual confirmation"
test "$confirmation" = ACCEPTED || stop failed "Manual acceptance was not confirmed"
manual_accepted=true

adb -s "$serial" exec-out screencap -p > "$report_dir/screenshot.png"
adb -s "$serial" shell uiautomator dump /sdcard/auto-secretary-acceptance.xml \
  > "$report_dir/ui-hierarchy-command.txt"
adb -s "$serial" pull /sdcard/auto-secretary-acceptance.xml \
  "$report_dir/ui-hierarchy.xml" > "$report_dir/ui-hierarchy-pull.txt"
adb -s "$serial" shell getprop > "$report_dir/device-properties.txt"
write_report accepted "Phase acceptance completed on the physical device"
echo "Device acceptance written to $report_dir"
