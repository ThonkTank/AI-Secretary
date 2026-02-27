package com.autosecretary.features.task.domain.scheduling;

import java.time.LocalDate;

public record SchedulingConflict(String taskId,
                                 String title,
                                 LocalDate day,
                                 ReasonCode reasonCode,
                                 String details) {

    public enum ReasonCode {
        OUTSIDE_WINDOW,
        CALENDAR_OVERLAP,
        PREREQUISITE_BLOCKED,
        NO_MATCHING_GAP
    }
}
