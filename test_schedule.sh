#!/usr/bin/env bash
set -euo pipefail

# === Konfiguration ===
ADB="/home/aaron/Android/Sdk/platform-tools/adb"
PACKAGE="com.autosecretary"
ACTIVITY=".app.MainActivity"
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

TODAY_DOW=$(date +%u) # 1=Mo, 2=Di, ..., 6=Sa, 7=So

# Check 1: Morgenroutine vor 07:00 geplant? (HIGH + perfekter Fit = immer erste Task)
MORGEN_SLOT=$(echo "$SUMMARY" | grep "Morgenroutine" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
if [ -n "$MORGEN_SLOT" ]; then
    MORGEN_START=$(echo "$MORGEN_SLOT" | cut -d'-' -f1)
    MORGEN_HOUR=$(echo "$MORGEN_START" | cut -d':' -f1 | sed 's/^0//')
    if [ "$MORGEN_HOUR" -lt 7 ]; then
        run_check "Morgenroutine vor 07:00 geplant ($MORGEN_SLOT)" "pass"
    else
        run_check "Morgenroutine vor 07:00 geplant (tatsaechlich: $MORGEN_SLOT)" "fail"
    fi
else
    run_check "Morgenroutine geplant (nicht in Zusammenfassung gefunden!)" "fail"
fi

# Check 2: Sport nur Mo/Mi/Fr (Weekday-Bug gefixt!)
SPORT_FOUND=$(echo "$SUMMARY" | grep "Sport:" | grep -c "slots" || true)
AUFWAERMEN_FOUND=$(echo "$SUMMARY" | grep "Aufwärmen:" | grep -c "slots" || true)
TRAINING_FOUND=$(echo "$SUMMARY" | grep "Training:" | grep -c "slots" || true)
if [ "$TODAY_DOW" = "1" ] || [ "$TODAY_DOW" = "3" ] || [ "$TODAY_DOW" = "5" ]; then
    # Mo/Mi/Fr — Sport sollte mit Kindern geplant sein
    if [ "$SPORT_FOUND" -gt 0 ] && [ "$AUFWAERMEN_FOUND" -gt 0 ] && [ "$TRAINING_FOUND" -gt 0 ]; then
        run_check "Sport + Aufwärmen + Training auf Mo/Mi/Fr geplant" "pass"
    elif [ "$SPORT_FOUND" -gt 0 ]; then
        run_check "Sport geplant, aber Kinder fehlen (Aufwärmen=$AUFWAERMEN_FOUND, Training=$TRAINING_FOUND)" "fail"
    else
        run_check "Sport nicht geplant (heute Mo/Mi/Fr, sollte geplant sein!)" "fail"
    fi
else
    # Andere Tage — Sport darf NICHT geplant sein
    if [ "$SPORT_FOUND" -eq 0 ]; then
        run_check "Sport nicht geplant (kein Mo/Mi/Fr — korrekt)" "pass"
    else
        run_check "Sport geplant, obwohl heute kein Mo/Mi/Fr!" "fail"
    fi
fi

# Check 3: Arbeit nur an Werktagen (Mo-Fr)
ARBEIT_SLOT=$(echo "$SUMMARY" | grep "Arbeit:" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
if [ "$TODAY_DOW" -le 5 ]; then
    # Werktag — Arbeit als langer Block
    if [ -n "$ARBEIT_SLOT" ]; then
        A_START_MIN=$(echo "$ARBEIT_SLOT" | cut -d'-' -f1 | awk -F: '{print $1*60+$2}')
        A_END_MIN=$(echo "$ARBEIT_SLOT" | cut -d'-' -f2 | awk -F: '{print $1*60+$2}')
        A_DURATION=$((A_END_MIN - A_START_MIN))
        if [ "$A_DURATION" -ge 60 ]; then
            run_check "Arbeit an Werktag als langer Block (${A_DURATION}min: $ARBEIT_SLOT)" "pass"
        else
            run_check "Arbeit zu kurz (${A_DURATION}min: $ARBEIT_SLOT, erwartet >=60)" "fail"
        fi
    else
        run_check "Arbeit nicht geplant (heute Werktag, sollte geplant sein!)" "fail"
    fi
else
    # Wochenende — Arbeit darf NICHT geplant sein
    ARBEIT_FOUND=$(echo "$SUMMARY" | grep "Arbeit:" | grep -c "slots" || true)
    if [ "$ARBEIT_FOUND" -eq 0 ]; then
        run_check "Arbeit nicht geplant (Wochenende — korrekt)" "pass"
    else
        run_check "Arbeit geplant, obwohl Wochenende!" "fail"
    fi
fi

# Check 4: Deadline-Tasks geplant (Steuererklärung UND Zahnarzttermin)
STEUER_FOUND=$(echo "$SUMMARY" | grep -c "Steuererklärung" || true)
ZAHNARZT_FOUND=$(echo "$SUMMARY" | grep -c "Zahnarzttermin" || true)
if [ "$STEUER_FOUND" -gt 0 ] && [ "$ZAHNARZT_FOUND" -gt 0 ]; then
    run_check "Deadline-Tasks geplant (Steuererklärung + Zahnarzttermin)" "pass"
elif [ "$STEUER_FOUND" -gt 0 ]; then
    run_check "Steuererklärung geplant, aber Zahnarzttermin fehlt!" "fail"
elif [ "$ZAHNARZT_FOUND" -gt 0 ]; then
    run_check "Zahnarzttermin geplant, aber Steuererklärung fehlt!" "fail"
else
    run_check "Keine Deadline-Tasks geplant!" "fail"
fi

# Check 5: Spanisch lernen geplant
SPANISCH_FOUND=$(echo "$SUMMARY" | grep "Spanisch" | grep -c "slots" || true)
if [ "$SPANISCH_FOUND" -gt 0 ]; then
    run_check "Spanisch lernen geplant" "pass"
else
    run_check "Spanisch lernen NICHT geplant" "fail"
fi

# Check 6: Einkaufen nur am Samstag
EINKAUFEN_FOUND=$(echo "$SUMMARY" | grep "Einkaufen" | grep -c "slots" || true)
if [ "$TODAY_DOW" = "6" ]; then
    if [ "$EINKAUFEN_FOUND" -gt 0 ]; then
        run_check "Einkaufen am Samstag geplant (korrekt)" "pass"
    else
        run_check "Einkaufen nicht geplant (heute Samstag, sollte geplant sein!)" "fail"
    fi
else
    if [ "$EINKAUFEN_FOUND" -eq 0 ]; then
        run_check "Einkaufen nicht geplant (kein Samstag — korrekt)" "pass"
    else
        run_check "Einkaufen geplant, obwohl heute kein Samstag!" "fail"
    fi
fi

# Check 7: Zeitliche Reihenfolge — Morgenroutine vor Arbeit (wenn beide geplant)
if [ -n "$MORGEN_SLOT" ] && [ -n "$ARBEIT_SLOT" ]; then
    M_START_MIN=$(echo "$MORGEN_SLOT" | cut -d'-' -f1 | awk -F: '{print $1*60+$2}')
    A_START_MIN2=$(echo "$ARBEIT_SLOT" | cut -d'-' -f1 | awk -F: '{print $1*60+$2}')
    if [ "$M_START_MIN" -lt "$A_START_MIN2" ]; then
        run_check "Zeitliche Reihenfolge: Morgenroutine vor Arbeit" "pass"
    else
        run_check "Zeitliche Reihenfolge: Morgenroutine ($MORGEN_SLOT) NICHT vor Arbeit ($ARBEIT_SLOT)" "fail"
    fi
else
    TOTAL=$((TOTAL + 1))
    PASS=$((PASS + 1))
    ok "Zeitliche Reihenfolge: Skip (nicht beide Tasks geplant)"
fi

# Check 8: Notfallplan aktualisieren — CRITICAL + überfällig + closeOnMiss=false → immer geplant
NOTFALL_FOUND=$(echo "$SUMMARY" | grep "Notfallplan" | grep -c "slots" || true)
if [ "$NOTFALL_FOUND" -gt 0 ]; then
    run_check "Notfallplan aktualisieren geplant (CRITICAL + closeOnMiss=false + abgelaufene Deadline)" "pass"
else
    run_check "Notfallplan aktualisieren NICHT geplant (sollte immer geplant sein!)" "fail"
fi

# Check 9: Abendspaziergang — nur Sonntag (TODAY_DOW=7)
SPAZIERGANG_FOUND=$(echo "$SUMMARY" | grep "Abendspaziergang" | grep -c "slots" || true)
if [ "$TODAY_DOW" = "7" ]; then
    if [ "$SPAZIERGANG_FOUND" -gt 0 ]; then
        run_check "Abendspaziergang am Sonntag geplant (korrekt)" "pass"
    else
        run_check "Abendspaziergang nicht geplant (heute Sonntag, sollte geplant sein!)" "fail"
    fi
else
    if [ "$SPAZIERGANG_FOUND" -eq 0 ]; then
        run_check "Abendspaziergang nicht geplant (kein Sonntag — korrekt)" "pass"
    else
        run_check "Abendspaziergang geplant, obwohl heute kein Sonntag!" "fail"
    fi
fi

# Check 10: Podcast hören — 3 Slots (repsPerDay=3)
PODCAST_SLOTS=$(echo "$SUMMARY" | grep "Podcast hören" | grep -oP '\d+ slots' | grep -oP '^\d+' || true)
if [ "${PODCAST_SLOTS:-0}" -ge 3 ]; then
    run_check "Podcast hören: $PODCAST_SLOTS Slots geplant (repsPerDay=3 korrekt)" "pass"
elif [ "${PODCAST_SLOTS:-0}" -gt 0 ]; then
    run_check "Podcast hören: nur $PODCAST_SLOTS Slots geplant (erwartet 3)" "fail"
else
    run_check "Podcast hören NICHT geplant (sollte 3x täglich geplant sein)" "fail"
fi

# Check 11: Wäsche aufhängen nach Wäsche waschen (Prereq zwischen nicht-verwandten Tasks)
WASCHEN_SLOT=$(echo "$SUMMARY" | grep "Wäsche waschen" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
AUFHAENGEN_SLOT=$(echo "$SUMMARY" | grep "Wäsche aufhängen" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | head -1 || true)
if [ -n "$WASCHEN_SLOT" ] && [ -n "$AUFHAENGEN_SLOT" ]; then
    WASCHEN_END_MIN=$(echo "$WASCHEN_SLOT" | cut -d'-' -f2 | awk -F: '{print $1*60+$2}')
    AUFHAENGEN_START_MIN=$(echo "$AUFHAENGEN_SLOT" | cut -d'-' -f1 | awk -F: '{print $1*60+$2}')
    if [ "$AUFHAENGEN_START_MIN" -ge "$WASCHEN_END_MIN" ]; then
        run_check "Wäsche aufhängen nach Wäsche waschen (Prereq korrekt: $WASCHEN_SLOT → $AUFHAENGEN_SLOT)" "pass"
    else
        run_check "Wäsche aufhängen VOR Wäsche waschen (Prereq verletzt!)" "fail"
    fi
elif [ -z "$WASCHEN_SLOT" ] && [ -z "$AUFHAENGEN_SLOT" ]; then
    run_check "Wäsche waschen + aufhängen nicht geplant (ggf. Wochenkontingent erfüllt — akzeptabel)" "pass"
elif [ -z "$WASCHEN_SLOT" ] && [ -n "$AUFHAENGEN_SLOT" ]; then
    run_check "Wäsche aufhängen geplant ohne Wäsche waschen (Prereq verletzt!)" "fail"
else
    run_check "Wäsche waschen geplant, Wäsche aufhängen fehlt (Prereq-Bug?)" "fail"
fi

# Check 12: Abendfenster — mindestens eine Task nach 16:00 geplant
EVENING_TASK=$(echo "$SUMMARY" | grep -oP '\d{2}:\d{2}-\d{2}:\d{2}' | awk -F'[-:]' '{h=$1; if (h+0 >= 16) print}' | head -1 || true)
if [ -n "$EVENING_TASK" ]; then
    run_check "Abendfenster genutzt (Task nach 16:00 geplant)" "pass"
else
    run_check "Abendfenster NICHT genutzt (keine Task nach 16:00)" "fail"
fi

# Check 13: Wochenbericht geplant (bi-weekly, perPeriod=2)
WOCHENBERICHT_FOUND=$(echo "$SUMMARY" | grep "Wochenbericht" | grep -c "slots" || true)
if [ "$WOCHENBERICHT_FOUND" -gt 0 ]; then
    run_check "Wochenbericht geplant (bi-weekly perPeriod=2 korrekt)" "pass"
else
    run_check "Wochenbericht NICHT geplant (perPeriod=2 Bug?)" "fail"
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
