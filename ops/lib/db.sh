#!/usr/bin/env bash

pull_schedule_db() {
    local db_local="/tmp/autosecretary.db"

    echo ""
    info "Ziehe SQLite-Datenbank vom Geraet..."

    if ! $ADB shell "run-as $PACKAGE cat databases/autosecretary.db" > "$db_local" 2>/dev/null; then
        warn "Konnte DB nicht ziehen (run-as fehlgeschlagen?)"
        return 1
    fi
    if [[ ! -s "$db_local" ]]; then
        warn "Gezogene Datenbank ist leer"
        return 1
    fi

    ok "Datenbank gespeichert: $db_local"
    if command -v sqlite3 >/dev/null 2>&1; then
        echo ""
        info "Task-Slots aus der DB (nach Tag gruppiert):"
        if ! sqlite3 "$db_local" "SELECT ts.day, tc.title, ts.start, ts.end, ts.score FROM task_slots ts JOIN task_core tc ON ts.taskId = tc.id WHERE ts.scheduled = 1 ORDER BY ts.day, ts.start;" 2>/dev/null; then
            warn "SQLite-Abfrage fehlgeschlagen"
        fi
    fi
}
