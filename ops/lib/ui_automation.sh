#!/usr/bin/env bash

check_device() {
    local devices serial
    devices="$($ADB devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print; exit }')"
    if [[ -z "$devices" ]]; then
        fail "Kein Geraet verbunden. Bitte USB-Debugging aktivieren."
        exit 1
    fi
    serial="$(awk '{print $1}' <<< "$devices")"
    ok "Geraet gefunden: $serial"
}

wake_device() {
    info "Wecke Geraet auf..."
    $ADB shell input keyevent KEYCODE_WAKEUP
    sleep 1
    $ADB shell input swipe 540 1800 540 600 300
    sleep 1
}

_fallback_tap_generate() {
    warn "Verwende Fallback-Koordinaten fuer 'GENERIEREN'"
    $ADB shell input tap 165 195
}

_dump_ui_hierarchy() {
    rm -f "$LOCAL_DUMP"

    if ! $ADB shell uiautomator dump "$UI_DUMP" >/dev/null 2>&1; then
        warn "UIAutomator-Dump auf dem Geraet fehlgeschlagen"
        return 1
    fi
    if ! $ADB pull "$UI_DUMP" "$LOCAL_DUMP" >/dev/null 2>&1; then
        warn "UIAutomator-Dump konnte nicht lokal kopiert werden"
        return 1
    fi
    [[ -f "$LOCAL_DUMP" ]]
}

_extract_bounds_for_button() {
    local button_text="$1"
    command -v python3 >/dev/null 2>&1 || return 1
    python3 - "$LOCAL_DUMP" "$button_text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, label = sys.argv[1], sys.argv[2].upper()
try:
    tree = ET.parse(path)
except Exception:
    sys.exit(0)

for node in tree.iter():
    text = (node.attrib.get("text") or "").upper()
    if text != label:
        continue
    bounds = node.attrib.get("bounds", "")
    if re.fullmatch(r"\[\d+,\d+\]\[\d+,\d+\]", bounds):
        print(bounds)
        sys.exit(0)
PY
}

tap_button() {
    local button_text="$1"
    local bounds
    local -a coords
    local cx cy

    info "Suche Button '$button_text' via UIAutomator..."

    if ! _dump_ui_hierarchy; then
        _fallback_tap_generate
        return
    fi

    if ! bounds="$(_extract_bounds_for_button "$button_text")"; then
        warn "Python 3 fehlt oder Bounds konnten nicht geparst werden"
        _fallback_tap_generate
        return
    fi
    if [[ -z "$bounds" ]]; then
        warn "Button '$button_text' nicht im UI-Dump gefunden"
        _fallback_tap_generate
        return
    fi

    mapfile -t coords < <(grep -oE '[0-9]+' <<< "$bounds")
    cx=$(( (coords[0] + coords[2]) / 2 ))
    cy=$(( (coords[1] + coords[3]) / 2 ))

    ok "Button '$button_text' gefunden bei [${coords[0]},${coords[1]}][${coords[2]},${coords[3]}] → Tap ($cx, $cy)"
    $ADB shell input tap "$cx" "$cy"
}
