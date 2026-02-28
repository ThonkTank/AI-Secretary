#!/usr/bin/env bash
# ops/lib/common.sh — Common utility functions for ops scripts

# Constants for parsing daily summaries
readonly SUMMARY_HEADER="Zusammenfassung"
readonly SLOTS_MARKER="slots"
readonly SUMMARY_FOOTER="Gesamt:"

# Helper to increment count when task found in current summary
_increment_summary_count() {
    local found_in_summary=$1
    if [ "$found_in_summary" -eq 1 ]; then
        return 0  # Return exit code indicating we should count
    fi
    return 1
}

# Counts how many days (from a 7-day summary) contain a task with slots
# Args: $1 = task_name (e.g., "Sport", "Einkaufen")
# Returns: number of days on which this task appears with slots
count_days_with_task() {
    local task_name="$1"
    local count=0
    local inside_daily_summary=0
    local task_found_in_current_summary=0

    while IFS= read -r line; do
        if echo "$line" | grep -q "$SUMMARY_HEADER"; then
            # Entering a new summary block: finalize previous summary
            if _increment_summary_count "$task_found_in_current_summary"; then
                count=$((count + 1))
            fi
            inside_daily_summary=1
            task_found_in_current_summary=0
        fi

        if [ "$inside_daily_summary" -eq 1 ] && \
           echo "$line" | grep -q "${task_name}:" && \
           echo "$line" | grep -q "$SLOTS_MARKER"; then
            task_found_in_current_summary=1
        fi

        if echo "$line" | grep -q "$SUMMARY_FOOTER"; then
            # Exiting summary block: finalize this summary
            if _increment_summary_count "$task_found_in_current_summary"; then
                count=$((count + 1))
            fi
            inside_daily_summary=0
            task_found_in_current_summary=0
        fi
    done <<< "$ALL_SUMMARIES"

    echo "$count"
}
