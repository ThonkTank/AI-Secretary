#!/usr/bin/env bash
# ops/lib/common.sh — Common utility functions for ops scripts

# Constants for parsing daily summaries
readonly SUMMARY_HEADER="Zusammenfassung"
readonly SLOTS_MARKER="slots"
readonly SUMMARY_FOOTER="Gesamt:"

# Counts how many days (from a 7-day summary) contain a task with slots
# Args: $1 = task_name (e.g., "Sport", "Einkaufen")
#       $2 = summaries_text (newline-separated summary blocks; if omitted, reads from ALL_SUMMARIES env var)
# Returns: number of days on which this task appears with slots
# Note: Each day's summary is bounded by SUMMARY_HEADER and SUMMARY_FOOTER markers.
#       Task counts as present if the day's summary contains "task_name:" with SLOTS_MARKER.
count_days_with_task() {
    local task_name="$1"
    local summaries_text="${2:-$ALL_SUMMARIES}"

    echo "$summaries_text" | awk -v task="$task_name" -v slots="$SLOTS_MARKER" '
        BEGIN { count = 0 }
        /Zusammenfassung/ {
            # At a new day boundary, count the previous day if it had the task
            if (task_found_in_day) count++
            task_found_in_day = 0
        }
        $0 ~ task ":" && $0 ~ slots {
            # Mark that we found the task in the current day
            task_found_in_day = 1
        }
        END {
            # Handle the last day (no Zusammenfassung after it)
            if (task_found_in_day) count++
            print count
        }
    '
}
