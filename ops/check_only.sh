#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-$(command -v adb || true)}"
PACKAGE="com.autosecretary.preview"
if [[ -z "$ADB" ]]; then
    echo "adb wurde nicht gefunden" >&2
    exit 1
fi
if [[ -z "$($ADB shell pidof "$PACKAGE" | tr -d '\r')" ]]; then
    echo "$PACKAGE läuft nicht" >&2
    exit 1
fi
if $ADB logcat -d -b crash | grep -q "$PACKAGE"; then
    $ADB logcat -d -b crash
    exit 1
fi
echo "OK: $PACKAGE läuft; kein Crash im Crash-Puffer"
