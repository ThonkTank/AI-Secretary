#!/usr/bin/env bash

build_debug_apk() {
    info "Baue APK (assembleDebug)..."
    (
        cd "$PROJECT_ROOT" || exit 1
        ./gradlew assembleDebug
    )
    ok "Build abgeschlossen"
}

install_debug_apk() {
    info "Deinstalliere alte Version (falls vorhanden)..."
    if ! $ADB shell pm uninstall "$PACKAGE" >/dev/null 2>&1; then
        warn "Vorherige Installation nicht entfernt oder nicht vorhanden"
    fi

    info "Installiere APK: $APK_PATH"
    $ADB install "$APK_PATH"
    ok "APK installiert"
}

clear_device_logcat() {
    $ADB logcat -c
    info "Logcat geleert"
}

start_schedule_app() {
    info "Starte App..."
    if ! $ADB shell am start -n "$PACKAGE/$ACTIVITY" >/dev/null; then
        fail "App konnte nicht gestartet werden"
        exit 1
    fi
    sleep 3
    ok "App gestartet, 3s gewartet"
}
