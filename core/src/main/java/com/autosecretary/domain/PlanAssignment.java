package com.autosecretary.domain;

import java.time.LocalDateTime;

public record PlanAssignment(
        WorkItem workItem,
        String occurrenceKey,
        LocalDateTime start,
        LocalDateTime end) { }
