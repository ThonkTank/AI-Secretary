package com.autosecretary.ui;

import java.time.LocalDateTime;
import java.util.List;

public record FocusRow(
        String id,
        String title,
        int durationMinutes,
        LocalDateTime suggestedStart,
        LocalDateTime suggestedEnd,
        List<StepRow> steps,
        String precedingCalendarTitle,
        boolean routine,
        boolean overdue) {
    public FocusRow {
        if (suggestedStart == null || suggestedEnd == null
                || !suggestedEnd.isAfter(suggestedStart)) {
            throw new IllegalArgumentException("Fokuszeit fehlt");
        }
        steps = List.copyOf(steps);
    }
}
