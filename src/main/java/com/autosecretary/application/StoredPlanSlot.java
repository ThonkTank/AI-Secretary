package com.autosecretary.application;

import java.time.LocalDateTime;

public record StoredPlanSlot(
        String id,
        String workItemId,
        String occurrenceKey,
        LocalDateTime start,
        LocalDateTime end,
        LocalDateTime computedAt) { }
