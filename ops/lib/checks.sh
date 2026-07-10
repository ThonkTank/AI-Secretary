#!/usr/bin/env bash

slot_start_minutes() { awk -F: '{print $1 * 60 + $2}' <<< "${1%%-*}"; }
slot_end_minutes() { awk -F: '{print $1 * 60 + $2}' <<< "${1##*-}"; }

run_check() {
    local name="$1"
    local result="$2"
    TOTAL=$((TOTAL + 1))
    if [[ "$result" == "pass" ]]; then
        PASS=$((PASS + 1))
        ok "$name"
    else
        fail "$name"
    fi
}

_count_matches() {
    local text="$1" needle="$2"
    awk -v needle="$needle" 'index($0, needle) { count++ } END { print count + 0 }' <<< "$text"
}

_count_task_slots() {
    local text="$1" task="$2"
    awk -v task="$task:" -v marker="$SLOTS_MARKER" \
        'index($0, task) && index($0, marker) { count++ } END { print count + 0 }' <<< "$text"
}

_first_slot_for_task() {
    local text="$1" task="$2"
    awk -v task="$task" '
        index($0, task) {
            if (match($0, /[0-9][0-9]:[0-9][0-9]-[0-9][0-9]:[0-9][0-9]/)) {
                print substr($0, RSTART, RLENGTH)
                exit
            }
        }
    ' <<< "$text"
}

_first_evening_slot() {
    local text="$1"
    awk '
        {
            while (match($0, /[0-9][0-9]:[0-9][0-9]-[0-9][0-9]:[0-9][0-9]/)) {
                slot = substr($0, RSTART, RLENGTH)
                hour = substr(slot, 1, 2) + 0
                if (hour >= 16) {
                    print slot
                    exit
                }
                $0 = substr($0, RSTART + RLENGTH)
            }
        }
    ' <<< "$text"
}

_extract_positive_score_exists() {
    local text="$1" task="$2"
    awk -v task="$task" '
        match($0, task ": [0-9]+") {
            value = substr($0, RSTART + length(task) + 2, RLENGTH - length(task) - 2) + 0
            if (value > 0) {
                print 1
                exit
            }
        }
        END { print 0 }
    ' <<< "$text" | tail -1
}

_extract_slots_number() {
    local text="$1" task="$2"
    awk -v task="$task" -v marker="$SLOTS_MARKER" '
        index($0, task) {
            if (match($0, /[0-9]+[[:space:]]+slots/)) {
                print substr($0, RSTART, RLENGTH) + 0
                found = 1
                exit
            }
        }
        END { if (!found) print 0 }
    ' <<< "$text" | tail -1
}

_print_checks_header() {
    local title="$1"
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  ${title}${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

run_schedule_checks() {
    local summary="$1"
    local log_output="$2"
    local today_dow="$3"
    local morgen_slot morgen_found sport_found aufwaermen_found training_found
    local arbeit_slot arbeit_found einkaufen_found notfall_found spaziergang_found
    local podcast_slots waschen_slot aufhaengen_slot evening_task wochenbericht_found
    local morgen_children morgen_grandchildren abend_found tagebuch_found hautpflege_found
    local notizen_found dehnen_found

    PASS=0
    TOTAL=0

    _print_checks_header "Automatische Checks (Heute)"

    morgen_slot="$(_first_slot_for_task "$summary" "Morgenroutine")"
    if [[ -n "$morgen_slot" ]]; then
        if (( $(slot_start_minutes "$morgen_slot") < 7 * 60 )); then
            run_check "Morgenroutine vor 07:00 geplant ($morgen_slot)" "pass"
        else
            run_check "Morgenroutine vor 07:00 geplant (tatsaechlich: $morgen_slot)" "fail"
        fi
    else
        run_check "Morgenroutine geplant (nicht in Zusammenfassung gefunden!)" "fail"
    fi

    sport_found="$(_count_task_slots "$summary" "Sport")"
    aufwaermen_found="$(_count_task_slots "$summary" "Aufwärmen")"
    training_found="$(_count_task_slots "$summary" "Training")"
    if [[ "$today_dow" == "1" || "$today_dow" == "3" || "$today_dow" == "5" ]]; then
        if (( sport_found > 0 && aufwaermen_found > 0 && training_found > 0 )); then
            run_check "Sport + Aufwärmen + Training auf Mo/Mi/Fr geplant" "pass"
        elif (( sport_found > 0 )); then
            run_check "Sport geplant, aber Kinder fehlen (Aufwärmen=$aufwaermen_found, Training=$training_found)" "fail"
        else
            if (( $(_extract_positive_score_exists "$log_output" "Sport") > 0 )); then
                run_check "Sport Day-Constraint korrekt (Score>0 am Mo/Mi/Fr, aber von höher priorisierten Tasks verdrängt)" "pass"
            else
                run_check "Sport nicht schedulable (Score=0 am Mo/Mi/Fr — Day-Constraint-Bug!)" "fail"
            fi
        fi
    else
        if (( sport_found == 0 )); then
            run_check "Sport nicht geplant (kein Mo/Mi/Fr — korrekt)" "pass"
        else
            run_check "Sport geplant, obwohl heute kein Mo/Mi/Fr!" "fail"
        fi
    fi

    arbeit_slot="$(_first_slot_for_task "$summary" "Arbeit")"
    if (( today_dow <= 5 )); then
        if [[ -n "$arbeit_slot" ]]; then
            local arbeit_duration
            arbeit_duration=$(( $(slot_end_minutes "$arbeit_slot") - $(slot_start_minutes "$arbeit_slot") ))
            if (( arbeit_duration >= 60 )); then
                run_check "Arbeit an Werktag als langer Block (${arbeit_duration}min: $arbeit_slot)" "pass"
            else
                run_check "Arbeit zu kurz (${arbeit_duration}min: $arbeit_slot, erwartet >=60)" "fail"
            fi
        else
            run_check "Arbeit nicht geplant (heute Werktag, sollte geplant sein!)" "fail"
        fi
    else
        arbeit_found="$(_count_task_slots "$summary" "Arbeit")"
        if (( arbeit_found == 0 )); then
            run_check "Arbeit nicht geplant (Wochenende — korrekt)" "pass"
        else
            run_check "Arbeit geplant, obwohl Wochenende!" "fail"
        fi
    fi

    local steuer_found zahnarzt_found spanisch_found
    steuer_found="$(_count_matches "$summary" "Steuererklärung")"
    zahnarzt_found="$(_count_matches "$summary" "Zahnarzttermin")"
    if (( steuer_found > 0 && zahnarzt_found > 0 )); then
        run_check "Deadline-Tasks geplant (Steuererklärung + Zahnarzttermin)" "pass"
    elif (( steuer_found > 0 )); then
        run_check "Steuererklärung geplant, aber Zahnarzttermin fehlt!" "fail"
    elif (( zahnarzt_found > 0 )); then
        run_check "Zahnarzttermin geplant, aber Steuererklärung fehlt!" "fail"
    else
        run_check "Keine Deadline-Tasks geplant!" "fail"
    fi

    spanisch_found="$(_count_task_slots "$summary" "Spanisch")"
    if (( spanisch_found > 0 )); then
        run_check "Spanisch lernen geplant" "pass"
    else
        run_check "Spanisch lernen NICHT geplant" "fail"
    fi

    einkaufen_found="$(_count_task_slots "$summary" "Einkaufen")"
    if [[ "$today_dow" == "6" ]]; then
        if (( einkaufen_found > 0 )); then
            run_check "Einkaufen am Samstag geplant (korrekt)" "pass"
        else
            run_check "Einkaufen nicht geplant (heute Samstag, sollte geplant sein!)" "fail"
        fi
    else
        if (( einkaufen_found == 0 )); then
            run_check "Einkaufen nicht geplant (kein Samstag — korrekt)" "pass"
        else
            run_check "Einkaufen geplant, obwohl heute kein Samstag!" "fail"
        fi
    fi

    if [[ -n "$morgen_slot" && -n "$arbeit_slot" ]]; then
        if (( $(slot_start_minutes "$morgen_slot") < $(slot_start_minutes "$arbeit_slot") )); then
            run_check "Zeitliche Reihenfolge: Morgenroutine vor Arbeit" "pass"
        else
            run_check "Zeitliche Reihenfolge: Morgenroutine ($morgen_slot) NICHT vor Arbeit ($arbeit_slot)" "fail"
        fi
    else
        run_check "Zeitliche Reihenfolge: Skip (nicht beide Tasks geplant)" "pass"
    fi

    notfall_found="$(_count_task_slots "$summary" "Notfallplan")"
    if (( notfall_found > 0 )); then
        run_check "Notfallplan aktualisieren geplant (CRITICAL + closeOnMiss=false + abgelaufene Deadline)" "pass"
    else
        run_check "Notfallplan aktualisieren NICHT geplant (sollte immer geplant sein!)" "fail"
    fi

    spaziergang_found="$(_count_task_slots "$summary" "Abendspaziergang")"
    if [[ "$today_dow" == "7" ]]; then
        if (( spaziergang_found > 0 )); then
            run_check "Abendspaziergang am Sonntag geplant (korrekt)" "pass"
        else
            run_check "Abendspaziergang nicht geplant (heute Sonntag, sollte geplant sein!)" "fail"
        fi
    else
        if (( spaziergang_found == 0 )); then
            run_check "Abendspaziergang nicht geplant (kein Sonntag — korrekt)" "pass"
        else
            run_check "Abendspaziergang geplant, obwohl heute kein Sonntag!" "fail"
        fi
    fi

    podcast_slots="$(_extract_slots_number "$summary" "Podcast hören")"
    if (( podcast_slots >= 3 )); then
        run_check "Podcast hören: $podcast_slots Slots geplant (repsPerDay=3 korrekt)" "pass"
    elif (( podcast_slots > 0 )); then
        run_check "Podcast hören: nur $podcast_slots Slots geplant (erwartet 3)" "fail"
    else
        run_check "Podcast hören NICHT geplant (sollte 3x täglich geplant sein)" "fail"
    fi

    waschen_slot="$(_first_slot_for_task "$summary" "Wäsche waschen")"
    aufhaengen_slot="$(_first_slot_for_task "$summary" "Wäsche aufhängen")"
    if [[ -n "$waschen_slot" && -n "$aufhaengen_slot" ]]; then
        if (( $(slot_start_minutes "$aufhaengen_slot") >= $(slot_end_minutes "$waschen_slot") )); then
            run_check "Wäsche aufhängen nach Wäsche waschen (Prereq korrekt: $waschen_slot → $aufhaengen_slot)" "pass"
        else
            run_check "Wäsche aufhängen VOR Wäsche waschen (Prereq verletzt!)" "fail"
        fi
    elif [[ -z "$waschen_slot" && -z "$aufhaengen_slot" ]]; then
        run_check "Wäsche waschen + aufhängen nicht geplant (ggf. Wochenkontingent erfüllt — akzeptabel)" "pass"
    elif [[ -z "$waschen_slot" ]]; then
        run_check "Wäsche aufhängen geplant ohne Wäsche waschen (Prereq verletzt!)" "fail"
    else
        run_check "Wäsche waschen geplant, Wäsche aufhängen fehlt (Prereq-Bug?)" "fail"
    fi

    evening_task="$(_first_evening_slot "$summary")"
    if [[ -n "$evening_task" ]]; then
        run_check "Abendfenster genutzt (Task nach 16:00 geplant)" "pass"
    else
        run_check "Abendfenster NICHT genutzt (keine Task nach 16:00)" "fail"
    fi

    wochenbericht_found="$(_count_task_slots "$summary" "Wochenbericht")"
    if (( wochenbericht_found > 0 )); then
        run_check "Wochenbericht geplant (bi-weekly perPeriod=2 korrekt)" "pass"
    else
        run_check "Wochenbericht NICHT geplant (perPeriod=2 Bug?)" "fail"
    fi

    morgen_found="$(_count_task_slots "$summary" "Morgenroutine")"
    local duschen_found zaehne_found fruehstueck_found haare_found abspuelen_found
    duschen_found="$(_count_task_slots "$summary" "Duschen")"
    zaehne_found="$(_count_task_slots "$summary" "Zähneputzen")"
    fruehstueck_found="$(_count_task_slots "$summary" "Frühstück")"
    haare_found="$(_count_task_slots "$summary" "Haare föhnen")"
    abspuelen_found="$(_count_task_slots "$summary" "Abspülen")"
    morgen_children=$(( duschen_found + zaehne_found + fruehstueck_found ))
    morgen_grandchildren=$(( haare_found + abspuelen_found ))
    if (( morgen_found > 0 && morgen_children >= 2 && morgen_grandchildren >= 1 )); then
        run_check "Morgenroutine: Parent + ${morgen_children} Kinder + ${morgen_grandchildren} Enkel geplant (3-Level-Baum)" "pass"
    elif (( morgen_found > 0 )); then
        run_check "Morgenroutine geplant, aber Kinder/Enkel fehlen (L1=$morgen_children, L2=$morgen_grandchildren)" "fail"
    else
        run_check "Morgenroutine NICHT geplant!" "fail"
    fi

    abend_found="$(_count_task_slots "$summary" "Abendroutine")"
    tagebuch_found="$(_count_task_slots "$summary" "Tagebuch schreiben")"
    hautpflege_found="$(_count_task_slots "$summary" "Hautpflege")"
    notizen_found="$(_count_task_slots "$summary" "Notizen ordnen")"
    if (( abend_found > 0 && tagebuch_found > 0 && hautpflege_found > 0 )); then
        local notiz_info=""
        (( notizen_found > 0 )) && notiz_info=" + Notizen ordnen (L2)"
        run_check "Abendroutine: Parent + Tagebuch + Hautpflege geplant${notiz_info}" "pass"
    elif (( abend_found > 0 )); then
        run_check "Abendroutine geplant, aber Kinder fehlen (Tagebuch=$tagebuch_found, Hautpflege=$hautpflege_found)" "fail"
    else
        run_check "Abendroutine NICHT geplant!" "fail"
    fi

    dehnen_found="$(_count_task_slots "$summary" "Dehnen")"
    if [[ "$today_dow" == "1" || "$today_dow" == "3" || "$today_dow" == "5" ]]; then
        if (( dehnen_found > 0 )); then
            run_check "Sport: Dehnen (L2 Sub-Sub-Task) auf Mo/Mi/Fr geplant" "pass"
        elif (( sport_found == 0 )); then
            run_check "Sport: Dehnen nicht geplant (Sport selbst verdrängt — akzeptabel)" "pass"
        else
            run_check "Sport: Dehnen NICHT geplant obwohl Sport geplant (Sub-Sub-Task-Bug!)" "fail"
        fi
    else
        if (( dehnen_found == 0 )); then
            run_check "Sport: Dehnen nicht geplant (kein Mo/Mi/Fr — korrekt)" "pass"
        else
            run_check "Sport: Dehnen geplant, obwohl heute kein Mo/Mi/Fr!" "fail"
        fi
    fi

    echo ""
    _print_checks_header "Multi-Day Verteilungs-Checks"

    local sport_days einkaufen_days arbeit_days morgen_days waesche_days spaziergang_days
    sport_days=$(count_days_with_task "Sport" "$ALL_SUMMARIES")
    if (( sport_days == 3 )); then
        run_check "Multi-Day: Sport auf $sport_days Tagen geplant (3x/Woche korrekt)" "pass"
    elif (( sport_days > 0 )); then
        run_check "Multi-Day: Sport auf $sport_days Tagen geplant (erwartet: 3)" "fail"
    else
        run_check "Multi-Day: Sport auf keinem Tag geplant!" "fail"
    fi

    einkaufen_days=$(count_days_with_task "Einkaufen" "$ALL_SUMMARIES")
    if (( einkaufen_days == 1 )); then
        run_check "Multi-Day: Einkaufen auf $einkaufen_days Tag geplant (1x/Woche Sa korrekt)" "pass"
    elif (( einkaufen_days > 1 )); then
        run_check "Multi-Day: Einkaufen auf $einkaufen_days Tagen geplant (erwartet: 1)" "fail"
    else
        run_check "Multi-Day: Einkaufen auf keinem Tag geplant!" "fail"
    fi

    arbeit_days=$(count_days_with_task "Arbeit" "$ALL_SUMMARIES")
    if (( arbeit_days == 5 )); then
        run_check "Multi-Day: Arbeit auf $arbeit_days Tagen geplant (Mo-Fr korrekt)" "pass"
    elif (( arbeit_days > 0 )); then
        run_check "Multi-Day: Arbeit auf $arbeit_days Tagen geplant (erwartet: 5)" "fail"
    else
        run_check "Multi-Day: Arbeit auf keinem Tag geplant!" "fail"
    fi

    morgen_days=$(count_days_with_task "Morgenroutine" "$ALL_SUMMARIES")
    if (( morgen_days == 7 )); then
        run_check "Multi-Day: Morgenroutine auf allen $morgen_days Tagen geplant (taeglich korrekt)" "pass"
    elif (( morgen_days > 0 )); then
        run_check "Multi-Day: Morgenroutine auf $morgen_days Tagen geplant (erwartet: 7)" "fail"
    else
        run_check "Multi-Day: Morgenroutine auf keinem Tag geplant!" "fail"
    fi

    waesche_days=$(count_days_with_task "Wäsche waschen" "$ALL_SUMMARIES")
    if (( waesche_days == 2 )); then
        run_check "Multi-Day: Wäsche waschen auf $waesche_days Tagen geplant (2x/Woche korrekt)" "pass"
    elif (( waesche_days > 0 )); then
        run_check "Multi-Day: Wäsche waschen auf $waesche_days Tagen geplant (erwartet: 2)" "fail"
    else
        run_check "Multi-Day: Wäsche waschen auf keinem Tag geplant!" "fail"
    fi

    spaziergang_days=$(count_days_with_task "Abendspaziergang" "$ALL_SUMMARIES")
    if (( spaziergang_days == 1 )); then
        run_check "Multi-Day: Abendspaziergang auf $spaziergang_days Tag geplant (1x/Woche So korrekt)" "pass"
    elif (( spaziergang_days > 1 )); then
        run_check "Multi-Day: Abendspaziergang auf $spaziergang_days Tagen geplant (erwartet: 1)" "fail"
    else
        run_check "Multi-Day: Abendspaziergang auf keinem Tag geplant!" "fail"
    fi
}

print_check_summary() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  Ergebnis: $PASS/$TOTAL Checks bestanden${NC}"
    echo -e "${CYAN}========================================${NC}"
}
