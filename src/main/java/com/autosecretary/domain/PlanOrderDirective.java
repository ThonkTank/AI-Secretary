package com.autosecretary.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanOrderDirective(
        LocalDate day,
        String workItemId,
        Relation relation,
        String anchorWorkItemId,
        LocalDateTime updatedAt) {
    public enum Relation { FIRST, BEFORE, AFTER, LAST }
}
