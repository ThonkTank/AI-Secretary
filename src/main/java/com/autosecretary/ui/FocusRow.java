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
        String precedingCalendarTitle) {
    public FocusRow { steps = List.copyOf(steps); }
}
