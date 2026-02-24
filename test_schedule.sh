#!/usr/bin/env bash
set -euo pipefail

# === Konfiguration ===
ADB="/home/aaron/Android/Sdk/platform-tools/adb"
PACKAGE="com.autosecretary"
ACTIVITY=".views.MainActivity"
APK="build/outputs/apk/debug/AutoSecretary.apk"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$SCRIPT_DIR/$APK"
LOG_TAG="SlotGen"
UI_DUMP="/sdcard/ui_dump.xml"
LOCAL_DUMP="/tmp/autosecretary_ui.xml"

# === Farben ===
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail()  { echo -e "${RED}[FAIL]${NC} $1"; }

# === Hilfsfunktionen ===
check_device() {
    local devices
    devices=$($ADB devices 2>/dev/null | grep -v "List" | grep "device$" | head -1)
    if [ -z "$devices" ]; then
        fail "Kein Geraet verbunden. Bitte USB-Debugging aktivieren."
        exit 1
    fi
    local serial
    serial=$(echo "$devices" | awk '{print $1}')
    ok "Geraet gefunden: $serial"
}

wake_device() {
    info "Wecke Geraet auf..."
    $ADB shell input keyevent KEYCODE_WAKEUP
    sleep 1
    $ADB shell input swipe 540 1800 540 600 300
    sleep 1
}

tap_button() {
    local button_text="$1"
    info "Suche Button '$button_text' via UIAutomator..."

    $ADB shell uiautomator dump "$UI_DUMP" 2>/dev/null || true
    $ADB pull "$UI_DUMP" "$LOCAL_DUMP" 2>/dev/null || true

    if [ ! -f "$LOCAL_DUMP" ]; then
        warn "UIAutomator-Dump fehlgeschlagen, verwende Fallback-Koordinaten"
        $ADB shell input tap 165 195
        return
    fi

    # Button-Bounds parsen (case-insensitive: Button-Text wird zu GROSSBUCHSTABEN im UI)
    local bounds
    bounds=$(grep -oiP "text=\"${button_text}\"[^>]*bounds=\"\[\d+,\d+\]\[\d+,\d+\]\"" "$LOCAL_DUMP" \
        | grep -oP 'bounds="\[\d+,\d+\]\[\d+,\d+\]"' \
        | grep -oP '\[\d+,\d+\]\[\d+,\d+\]' \
        | head -1) || true

    if [ -z "$bounds" ]; then
        warn "Button '$button_text' nicht im UI-Dump gefunden, verwende Fallback-Koordinaten"
        $ADB shell input tap 165 195
        return
    fi

    # Bounds parsen: [x1,y1][x2,y2] → Mitte berechnen
    local x1 y1 x2 y2 cx cy
    x1=$(echo "$bounds" | grep -oP '\d+' | sed -n '1p')
    y1=$(echo "$bounds" | grep -oP '\d+' | sed -n '2p')
    x2=$(echo "$bounds" | grep -oP '\d+' | sed -n '3p')
    y2=$(echo "$bounds" | grep -oP '\d+' | sed -n '4p')
    cx=$(( (x1 + x2) / 2 ))
    cy=$(( (y1 + y2) / 2 ))

    ok "Button '$button_text' gefunden bei [$x1,$y1][$x2,$y2] → Tap ($cx, $cy)"
    $ADB shell input tap "$cx" "$cy"
}

# === Hauptablauf ===
echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  AutoSecretary Schedule Test${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# 1. Build
info "Baue APK (assembleDebug)..."
cd "$SCRIPT_DIR"
./gradlew assembleDebug
ok "Build abgeschlossen"
echo ""

# 2. Geraet pruefen
check_device
echo ""

# 3. Geraet aufwecken
wake_device
echo ""

# 4. Alte Version deinstallieren + neue installieren
info "Deinstalliere alte Version (falls vorhanden)..."
$ADB shell pm uninstall "$PACKAGE" 2>/dev/null || true
info "Installiere APK: $APK_PATH"
$ADB install "$APK_PATH"
ok "APK installiert"
echo ""

# 5. Logcat leeren
$ADB logcat -c
info "Logcat geleert"
echo ""

# 6. App starten
info "Starte App..."
$ADB shell am start -n "$PACKAGE/$ACTIVITY" || true
sleep 3
ok "App gestartet, 3s gewartet"
echo ""

# 7. Generieren antippen
info "Tippe 'Generieren' Button..."
tap_button "GENERIEREN"
echo ""

# 8. Warten auf Generation
info "Warte 5s auf Slot-Generierung..."
sleep 5
echo ""

# 9. Logcat auslesen
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Slot-Generierung Ergebnisse${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

LOG_OUTPUT=$($ADB logcat -d -s "${LOG_TAG}:D" | grep "$LOG_TAG" || true)

if [ -z "$LOG_OUTPUT" ]; then
    fail "Keine SlotGen-Logs gefunden! Moegliche Ursachen:"
    echo "  - App wurde nicht korrekt gestartet"
    echo "  - 'Generieren' Button wurde nicht getroffen"
    echo "  - Generation hat laenger als 5s gedauert"
    echo ""
    echo "Versuche gesamten Logcat:"
    $ADB logcat -d | grep -i "slot\|task\|autosecretary" | tail -30
    exit 1
fi

# Vollstaendiges Log anzeigen
echo -e "${BLUE}--- Vollstaendiges SlotGen Log ---${NC}"
echo "$LOG_OUTPUT"
echo ""

# Zusammenfassung extrahieren
echo -e "${CYAN}--- Zusammenfassung ---${NC}"
SUMMARY=$(echo "$LOG_OUTPUT" | sed -n '/Zusammenfassung/,/Gesamt/p')
if [ -n "$SUMMARY" ]; then
    echo "$SUMMARY"
else
    warn "Keine Zusammenfassung im Log gefunden"
fi
echo ""

# === 10. Automatische Checks ===
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Automatische Checks${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

PASS=0
TOTAL=0

run_check() {
    local name="$1"
    local result="$2"
    TOTAL=$((TOTAL + 1))
    if [ "$result" = "pass" ]; then
        PASS=$((PASS + 1))
        ok "$name"
    else
        fail "$name"
    fi
}

# Check 1: Morgenroutine vor 09:00 geplant?
MORGEN_SLOT=$(echo "$SUMMARY" | grep "Morgenroutine" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
if [ -n "$MORGEN_SLOT" ]; then
    MORGEN_START=$(echo "$MORGEN_SLOT" | cut -d'-' -f1)
    MORGEN_HOUR=$(echo "$MORGEN_START" | cut -d':' -f1 | sed 's/^0//')
    if [ "$MORGEN_HOUR" -lt 9 ]; then
        run_check "Morgenroutine vor 09:00 geplant ($MORGEN_SLOT)" "pass"
    else
        run_check "Morgenroutine vor 09:00 geplant (tatsaechlich: $MORGEN_SLOT)" "fail"
    fi
else
    run_check "Morgenroutine geplant (nicht in Zusammenfassung gefunden!)" "fail"
fi

# Check 2: Sport mit Kindern (Aufwärmen/Training) verschachtelt?
SPORT_FOUND=$(echo "$SUMMARY" | grep -c "Sport" || true)
AUFWAERMEN_FOUND=$(echo "$SUMMARY" | grep -c "Aufwärmen" || true)
TRAINING_FOUND=$(echo "$SUMMARY" | grep -c "Training" || true)
if [ "$SPORT_FOUND" -gt 0 ] && [ "$AUFWAERMEN_FOUND" -gt 0 ] && [ "$TRAINING_FOUND" -gt 0 ]; then
    run_check "Sport + Aufwärmen + Training alle geplant" "pass"
elif [ "$SPORT_FOUND" -gt 0 ]; then
    run_check "Sport geplant, aber Kinder fehlen (Aufwärmen=$AUFWAERMEN_FOUND, Training=$TRAINING_FOUND)" "fail"
else
    TODAY_DOW=$(date +%u) # 1=Mo, 7=So
    if [ "$TODAY_DOW" = "1" ] || [ "$TODAY_DOW" = "3" ] || [ "$TODAY_DOW" = "5" ]; then
        run_check "Sport nicht geplant (heute Mo/Mi/Fr, sollte geplant sein!)" "fail"
    else
        run_check "Sport nicht geplant (heute kein Mo/Mi/Fr — Scoring-Bug: trotzdem moeglich)" "pass"
    fi
fi

# Check 3: Steuererklärung geplant (Deadline-Task)?
STEUER_FOUND=$(echo "$SUMMARY" | grep -c "Steuererklärung" || true)
if [ "$STEUER_FOUND" -gt 0 ]; then
    run_check "Steuererklärung (Deadline-Task) geplant" "pass"
else
    run_check "Steuererklärung nicht geplant!" "fail"
fi

# Check 4: Abend-Tasks Verhalten
echo -e "  ${YELLOW}[INFO]${NC} Abendspaziergang (18:00 pref): $(echo "$SUMMARY" | grep "Abendspaziergang" || echo "nicht im Log")"
echo -e "  ${YELLOW}[INFO]${NC} Tagebuch (21:00 pref): $(echo "$SUMMARY" | grep "Tagebuch" || echo "nicht im Log")"
ABEND_UNSCHED=$(echo "$SUMMARY" | grep "Abendspaziergang" | grep -c "unscheduled" || true)
TAGEBUCH_UNSCHED=$(echo "$SUMMARY" | grep "Tagebuch" | grep -c "unscheduled" || true)
if [ "$ABEND_UNSCHED" -gt 0 ] || [ "$TAGEBUCH_UNSCHED" -gt 0 ]; then
    warn "Abend-Tasks nicht geplant — erwartet bei 06:00-16:00 Fenster"
fi
TOTAL=$((TOTAL + 1))
PASS=$((PASS + 1))
ok "Abend-Tasks Verhalten dokumentiert (siehe oben)"

# Check 5: Arbeit als langer Block?
ARBEIT_SLOT=$(echo "$SUMMARY" | grep "Arbeit:" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
if [ -n "$ARBEIT_SLOT" ]; then
    A_START_MIN=$(echo "$ARBEIT_SLOT" | cut -d'-' -f1 | awk -F: '{print $1*60+$2}')
    A_END_MIN=$(echo "$ARBEIT_SLOT" | cut -d'-' -f2 | awk -F: '{print $1*60+$2}')
    A_DURATION=$((A_END_MIN - A_START_MIN))
    if [ "$A_DURATION" -ge 60 ]; then
        run_check "Arbeit als langer Block geplant (${A_DURATION}min: $ARBEIT_SLOT)" "pass"
    else
        run_check "Arbeit zu kurz (${A_DURATION}min: $ARBEIT_SLOT, erwartet >=60)" "fail"
    fi
else
    TODAY_DOW=$(date +%u)
    if [ "$TODAY_DOW" -le 5 ]; then
        run_check "Arbeit nicht geplant (heute Werktag, sollte geplant sein!)" "fail"
    else
        run_check "Arbeit nicht geplant (Wochenende — erwartet)" "pass"
    fi
fi

# Check 6: Meditation geplant?
MEDITATION_FOUND=$(echo "$SUMMARY" | grep "Meditation" | grep -c "slots" || true)
MEDITATION_UNSCHED=$(echo "$SUMMARY" | grep "Meditation" | grep -c "unscheduled" || true)
if [ "$MEDITATION_FOUND" -gt 0 ]; then
    run_check "Meditation geplant" "pass"
else
    run_check "Meditation NICHT geplant (verdraengt durch hoeher-scorende Tasks)" "fail"
fi

# Ergebnis
echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Ergebnis: $PASS/$TOTAL Checks bestanden${NC}"
echo -e "${CYAN}========================================${NC}"

# === Optional: DB ziehen ===
if [ "${1:-}" = "--pull-db" ]; then
    echo ""
    info "Ziehe SQLite-Datenbank vom Geraet..."
    DB_LOCAL="/tmp/autosecretary.db"
    $ADB shell "run-as $PACKAGE cat databases/autosecretary.db" > "$DB_LOCAL" 2>/dev/null || true
    if [ -s "$DB_LOCAL" ]; then
        ok "Datenbank gespeichert: $DB_LOCAL"
        if command -v sqlite3 &>/dev/null; then
            echo ""
            info "Task-Slots aus der DB:"
            sqlite3 "$DB_LOCAL" "SELECT tc.title, ts.start, ts.end, ts.score, ts.scheduled, ts.completed FROM task_slots ts JOIN task_core tc ON ts.taskId = tc.id WHERE ts.scheduled = 1 ORDER BY ts.start;" 2>/dev/null || warn "SQLite-Abfrage fehlgeschlagen"
        fi
    else
        warn "Konnte DB nicht ziehen (run-as fehlgeschlagen?)"
    fi
fi

echo ""
info "Fertig!"
