package com.autosecretary.application;

import java.time.LocalDateTime;

public record StoredPlanningConflict(
        String id,
        String workItemId,
        String occurrenceKey,
        String reason,
        String detail,
        LocalDateTime computedAt) { }
