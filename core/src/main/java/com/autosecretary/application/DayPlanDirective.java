package com.autosecretary.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DayPlanDirective(
        String id,
        LocalDate day,
        String workItemId,
        Relation relation,
        String anchorWorkItemId,
        LocalDateTime updatedAt) {
    public enum Relation { FIRST, BEFORE, AFTER, LAST, OMIT }
}
