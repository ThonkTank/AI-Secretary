package com.autosecretary.application;

import java.util.List;

public sealed interface CalendarReadResult
        permits CalendarReadResult.Available, CalendarReadResult.PermissionMissing {
    record Available(List<CalendarOccurrence> occurrences) implements CalendarReadResult {
        public Available {
            occurrences = List.copyOf(occurrences);
        }
    }

    record PermissionMissing() implements CalendarReadResult { }
}
