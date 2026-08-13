#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-$(command -v adb || true)}"
PACKAGE="com.autosecretary.preview"
ACTIVITY="com.autosecretary.ui.MainActivity"
APK="$PROJECT_ROOT/build/outputs/apk/dev/debug/AutoSecretary-devDebug.apk"

if [[ -z "$ADB" ]]; then
    echo "adb wurde nicht gefunden; setze ADB=/pfad/zu/adb" >&2
    exit 1
fi
if [[ -z "$($ADB devices | sed -n '2{/device$/p;}')" ]]; then
    echo "Kein freigegebenes Android-Gerät verbunden" >&2
    exit 1
fi

cd "$PROJECT_ROOT"
./gradlew checkArchitecture assembleDevDebug
test -f "$APK"

$ADB install -r "$APK"
$ADB logcat -c
$ADB shell am force-stop "$PACKAGE"
$ADB shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
sleep 3

if [[ -z "$($ADB shell pidof "$PACKAGE" | tr -d '\r')" ]]; then
    echo "Preview-App läuft nach dem Start nicht" >&2
    exit 1
fi

$ADB shell uiautomator dump /sdcard/autosecretary-smoke.xml >/dev/null
$ADB pull /sdcard/autosecretary-smoke.xml /tmp/autosecretary-smoke.xml >/dev/null
if grep -q 'text="Keine Altdaten vorhanden"' /tmp/autosecretary-smoke.xml; then
    bounds="$(sed -n 's/.*text="Keine Altdaten vorhanden".*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' /tmp/autosecretary-smoke.xml | head -1)"
    read -r left top right bottom <<< "$bounds"
    [[ -n "${bottom:-}" ]] || { echo "First-run-Auswahl konnte nicht lokalisiert werden" >&2; exit 1; }
    $ADB shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
    sleep 2
    $ADB shell uiautomator dump /sdcard/autosecretary-smoke.xml >/dev/null
    $ADB pull /sdcard/autosecretary-smoke.xml /tmp/autosecretary-smoke.xml >/dev/null
fi
if ! grep -Eq 'heute|alles ansehen|lokale KI' /tmp/autosecretary-smoke.xml; then
    echo "Hauptnavigation wurde im UI-Dump nicht gefunden" >&2
    exit 1
fi

if $ADB logcat -d -b crash | grep -q "$PACKAGE"; then
    $ADB logcat -d -b crash
    echo "Crash der Preview-App erkannt" >&2
    exit 1
fi

echo "OK: Tests, Preview-Build, Installation, Start und Hauptnavigation"
echo "Manuell prüfen: Aufgabe anlegen/bearbeiten/abschließen, Routine-Schritte, Sortierung und Widget."
