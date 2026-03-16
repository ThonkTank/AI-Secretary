#!/usr/bin/env bash

collect_schedule_logs() {
    local raw_log filtered_log

    if ! raw_log="$($ADB logcat -d -s "${LOG_TAG}:D" 2>/dev/null)"; then
        fail "Logcat fuer ${LOG_TAG} konnte nicht gelesen werden"
        exit 1
    fi

    filtered_log="$(awk -v tag="$LOG_TAG" 'index($0, tag) { print }' <<< "$raw_log")"
    if [[ -z "$filtered_log" ]]; then
        fail "Keine SlotGen-Logs gefunden! Moegliche Ursachen:"
        echo "  - App wurde nicht korrekt gestartet"
        echo "  - 'Generieren' Button wurde nicht getroffen"
        echo "  - Generation hat laenger als 10s gedauert"
        echo ""
        echo "Versuche gesamten Logcat:"
        if ! $ADB logcat -d | grep -i "slot\\|task\\|autosecretary" | tail -30; then
            warn "Kein zusaetzlicher Kontext im gesamten Logcat gefunden"
        fi
        exit 1
    fi

    printf '%s\n' "$filtered_log"
}

extract_all_summaries() {
    sed -n "/${SUMMARY_HEADER}/,/^.*${SUMMARY_FOOTER}/p" <<< "$1"
}

extract_today_summary() {
    sed -n "/${SUMMARY_HEADER}/{:a;/${SUMMARY_FOOTER}/!{N;ba};p;q}" <<< "$1"
}

print_week_plan() {
    local all_summaries="$1"

    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  7-Tage Wochenplan${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""

    if [[ -z "$all_summaries" ]]; then
        warn "Keine Zusammenfassungen im Log gefunden"
        echo ""
        return
    fi

    while IFS= read -r line; do
        if grep -q "$SUMMARY_HEADER" <<< "$line"; then
            echo ""
            echo -e "${BLUE}${line}${NC}"
        elif grep -q "$SUMMARY_FOOTER" <<< "$line"; then
            echo -e "${YELLOW}${line}${NC}"
        elif grep -q "unscheduled" <<< "$line"; then
            :
        elif grep -q "$SLOTS_MARKER" <<< "$line"; then
            echo -e "  ${GREEN}${line}${NC}"
        else
            echo "$line"
        fi
    done <<< "$all_summaries"
    echo ""
}
