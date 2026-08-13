#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$(command -v adb || true)}"
APKSIGNER="${APKSIGNER:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/apksigner}"
AAPT="${AAPT:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/aapt}"
PACKAGE="com.autosecretary"
ACTIVITY=".ui.MainActivity"
BUILD4_CERT="1e0e90509d79efacebaec1af024f2577d7799cf5534e841db7417184287dbfb2"
FALLBACK_CERT="79eb85409ede6aa014b125dd6190206c9809f0b948a5f92342a99376f81d0fef"

verify_archive_sidecar() {
    local archive="$1" expected actual
    [[ -f "$archive" && -f "$archive.sha256" ]] || {
        echo "Fallback benötigt Archiv plus gleichnamige .sha256-Datei" >&2
        exit 1
    }
    expected="$(awk 'NR == 1 { print tolower($1) }' "$archive.sha256")"
    [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || {
        echo "Ungültige SHA-256-Sidecar" >&2
        exit 1
    }
    actual="$(sha256sum "$archive" | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] || {
        echo "Archiv-Prüfsumme stimmt nicht" >&2
        exit 1
    }
    unzip -tq "$archive"
}

usage() {
    echo "KI-/Preview-Gate: $0 ai" >&2
    echo "Direktes Upgrade: $0 direct V2.0.0.apk V2.0.1.apk" >&2
    echo "Key-Fallback:     $0 fallback BUILD4-EXPORT.zip V2.0.0.apk V2.0.1.apk" >&2
    exit 2
}

[[ -n "$ADB" ]] || {
    echo "adb muss verfügbar sein" >&2
    exit 1
}
[[ $# -ge 1 ]] || usage
mode="$1"
shift

run_ai_gate() {
    echo "Führe 20 typisierte deutsche Befehle, sichere Ablehnungen und echte Modellinferenz aus."
    (
        cd "$ROOT"
        ./gradlew --no-daemon --max-workers=1 assembleFullDebug assembleFullDebugAndroidTest
    )
    local preview_apk="$ROOT/build/outputs/apk/full/debug/AutoSecretary-fullDebug.apk"
    local test_apk="$ROOT/build/outputs/apk/androidTest/full/debug/AutoSecretary-full-debug-androidTest.apk"
    [[ -f "$preview_apk" && -f "$test_apk" ]] || {
        echo "Full-Debug-Testartefakte fehlen" >&2
        exit 1
    }
    $ADB install -r "$preview_apk"
    $ADB install -r "$test_apk"
    local instrumentation_output
    instrumentation_output="$($ADB shell am instrument -w \
        com.autosecretary.preview.test/androidx.test.runner.AndroidJUnitRunner)"
    printf '%s\n' "$instrumentation_output"
    grep -q 'OK (3 tests)' <<< "$instrumentation_output"
}

if [[ "$mode" == "ai" ]]; then
    [[ $# -eq 0 ]] || usage
    run_ai_gate
    exit 0
fi

[[ -x "$APKSIGNER" && -x "$AAPT" ]] || {
    echo "adb, apksigner und aapt müssen verfügbar sein" >&2
    exit 1
}
[[ -n "$($ADB devices | sed -n '2{/device$/p;}')" ]] || {
    echo "Kein freigegebenes Android-Gerät verbunden" >&2
    exit 1
}
archive=""
if [[ "$mode" == "direct" ]]; then
    [[ $# -eq 2 ]] || usage
    v2_apk="$1"
    next_apk="$2"
    expected_cert="$BUILD4_CERT"
    identity="build4"
elif [[ "$mode" == "fallback" ]]; then
    [[ $# -eq 3 ]] || usage
    archive="$1"
    v2_apk="$2"
    next_apk="$3"
    expected_cert="$FALLBACK_CERT"
    identity="fallback"
else
    usage
fi

for apk in "$v2_apk" "$next_apk"; do
    [[ -f "$apk" ]] || { echo "APK fehlt: $apk" >&2; exit 1; }
    "$APKSIGNER" verify "$apk"
    package="$($AAPT dump badging "$apk" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
    [[ "$package" == "$PACKAGE" ]] || { echo "Falsches Paket: $package" >&2; exit 1; }
    cert="$($APKSIGNER verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')"
    [[ "$cert" == "$expected_cert" ]] || { echo "Unerwartetes Release-Zertifikat" >&2; exit 1; }
done
v2_code="$($AAPT dump badging "$v2_apk" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
next_code="$($AAPT dump badging "$next_apk" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
v2_name="$($AAPT dump badging "$v2_apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
next_name="$($AAPT dump badging "$next_apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
[[ "$v2_code" == "6" && "$v2_name" == "2.0.0" \
    && "$next_code" == "7" && "$next_name" == "2.0.1" ]] || {
    echo "Erwartet werden exakt v2.0.0/code 6 und v2.0.1/code 7" >&2
    exit 1
}

run_ai_gate

installed_apk="$($ADB shell pm path "$PACKAGE" | sed -n 's/^package://p' | head -1 | tr -d '\r')"
[[ -n "$installed_apk" ]] || { echo "Build 4 ist nicht installiert" >&2; exit 1; }
temporary_apk="$(mktemp)"
production_mutated=false
production_restored=false
cleanup() {
    rm -f "$temporary_apk"
    if [[ "$production_mutated" == true && "$production_restored" != true ]]; then
        echo "ACHTUNG: Das Produktions-Gate wurde nach der ersten Paketänderung abgebrochen." >&2
        echo "Nicht normal weiterverwenden; code 6 und das extern gesicherte Archiv wiederherstellen." >&2
    fi
}
trap cleanup EXIT
$ADB exec-out cat "$installed_apk" > "$temporary_apk"
installed_cert="$($APKSIGNER verify --print-certs "$temporary_apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')"
[[ "$installed_cert" == "$BUILD4_CERT" ]] || { echo "Installierte App ist nicht Build 4" >&2; exit 1; }
installed_source_code="$($AAPT dump badging "$temporary_apk" \
    | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
[[ "$installed_source_code" =~ ^[0-9]+$ && "$installed_source_code" -lt "$v2_code" ]] || {
    echo "Installierte Ausgangsversion ist kein echtes Upgradeziel" >&2
    exit 1
}

if [[ "$mode" == "direct" ]]; then
    export_output="$("$ROOT/ops/adb_database_bridge.sh" export "$PACKAGE")"
    printf '%s\n' "$export_output"
    archive="$(sed -n 's/^Verifiziertes Build-4-Archiv: //p' <<< "$export_output" | tail -1)"
    verify_archive_sidecar "$archive"
    echo "Vor dem echten Upgrade muss auch der soeben erzeugte Export extern gesichert sein."
    read -r -p "Zum Fortfahren exakt ARCHIV-EXTERN-GESICHERT eingeben: " confirmation
    [[ "$confirmation" == "ARCHIV-EXTERN-GESICHERT" ]] || exit 1
    production_mutated=true
    $ADB install -r "$v2_apk"
else
    verify_archive_sidecar "$archive"
    "$ROOT/ops/adb_database_bridge.sh" stage "$archive"
    echo "Der nächste Schritt deinstalliert exakt $PACKAGE. Das verifizierte Archiv muss extern gesichert sein."
    read -r -p "Zum Fortfahren exakt ARCHIV-EXTERN-GESICHERT eingeben: " confirmation
    [[ "$confirmation" == "ARCHIV-EXTERN-GESICHERT" ]] || exit 1
    production_mutated=true
    $ADB uninstall "$PACKAGE"
    $ADB install "$v2_apk"
fi

$ADB shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
if [[ "$mode" == "fallback" ]]; then
    echo "Importiere jetzt in der App das bereitgestellte Build-4-Archiv."
    read -r -p "Nach erfolgreichem Mengenvergleich exakt IMPORT-PASSED eingeben: " import_result
    [[ "$import_result" == "IMPORT-PASSED" ]] || exit 1
fi

$ADB shell uiautomator dump /sdcard/autosecretary-gate.xml >/dev/null
ui_dump="$(mktemp)"
$ADB pull /sdcard/autosecretary-gate.xml "$ui_dump" >/dev/null
grep -q 'Datenbank 34' "$ui_dump"
rm -f "$ui_dump"

read -r -p "Gerät wird für den Boot-Gate neu gestartet. Exakt REBOOT eingeben: " reboot_confirmation
[[ "$reboot_confirmation" == "REBOOT" ]] || exit 1
$ADB reboot
$ADB wait-for-device
$ADB shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
[[ -n "$($ADB shell pidof "$PACKAGE" | tr -d '\r')" ]]

echo "Prüfe jetzt einen Tageswechsel und eine Kalenderänderung; Widget und Fokusplan müssen reagieren."
read -r -p "Nach beiden Nachweisen exakt DAY-AND-CALENDAR-PASSED eingeben: " autonomy_result
[[ "$autonomy_result" == "DAY-AND-CALENDAR-PASSED" ]] || exit 1

$ADB install -r "$next_apk"
installed_code="$($ADB shell dumpsys package "$PACKAGE" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)"
[[ "$installed_code" == "$next_code" ]]

echo "Der Update-Nachweis ist erbracht. Jetzt wird das extern gesicherte Ausgangsarchiv"
echo "unter dem veröffentlichbaren code-6-Kandidaten wiederhergestellt."
"$ROOT/ops/adb_database_bridge.sh" stage "$archive"
read -r -p "Für die Rückkehr zu code 6 exakt QUELLDATEN-WIEDERHERSTELLEN eingeben: " restore_confirmation
[[ "$restore_confirmation" == "QUELLDATEN-WIEDERHERSTELLEN" ]] || exit 1
$ADB uninstall "$PACKAGE"
$ADB install "$v2_apk"
$ADB shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
echo "Importiere dasselbe extern gesicherte Build-4-Archiv erneut in code 6."
read -r -p "Nach erfolgreichem Mengenvergleich exakt RESTORE-PASSED eingeben: " restore_result
[[ "$restore_result" == "RESTORE-PASSED" ]] || exit 1
$ADB shell uiautomator dump /sdcard/autosecretary-restore-gate.xml >/dev/null
restore_ui_dump="$(mktemp)"
$ADB pull /sdcard/autosecretary-restore-gate.xml "$restore_ui_dump" >/dev/null
grep -q 'Datenbank 34' "$restore_ui_dump"
rm -f "$restore_ui_dump"
restored_code="$($ADB shell dumpsys package "$PACKAGE" \
    | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)"
[[ "$restored_code" == "$v2_code" ]]
production_restored=true

mkdir -p "$ROOT/ops/local-gates"
stamp="$(date +%Y%m%d-%H%M%S-%N)"
report="$ROOT/ops/local-gates/device-gate-$stamp.properties"
{
    echo "status=PASSED"
    echo "targetVersionCode=6"
    echo "sourceVersionCode=$installed_source_code"
    echo "signingIdentity=$identity"
    echo "evidence=SET_AFTER_REPORT_UPLOAD"
    echo "build4Upgrade=PASSED"
    echo "databaseVersion=34"
    echo "reboot=PASSED"
    echo "dayRollover=PASSED"
    echo "calendarRefresh=PASSED"
    echo "updateToNextVersion=PASSED"
    echo "restoredTargetVersion=PASSED"
    echo "aiGermanCases=PASSED"
    echo "aiInvalidOutputRejection=PASSED"
    echo "aiNoMutationBeforeConfirmation=PASSED"
    echo "device=$($ADB shell getprop ro.product.model | tr -d '\r')"
    echo "android=$($ADB shell getprop ro.build.version.release | tr -d '\r')"
    echo "createdAt=$(date --iso-8601=seconds)"
    echo "targetApkSha256=$(sha256sum "$v2_apk" | awk '{print $1}')"
    echo "nextGateApkSha256=$(sha256sum "$next_apk" | awk '{print $1}')"
    echo "sourceArchiveSha256=$(sha256sum "$archive" | awk '{print $1}')"
} > "$report"
(cd "$(dirname "$report")" && sha256sum "$(basename "$report")" \
    > "$(basename "$report").sha256")
echo "Geräte-Gate bestanden: $report"
echo "Report extern archivieren, Evidence-URL eintragen und upgrade-gate.properties separat reviewen."
