package com.autosecretary.application;

import java.time.LocalDateTime;

public record MigrationCandidate(
        String id,
        String title,
        int durationMinutes,
        LocalDateTime deadlineAt,
        String reason,
        String legacySummary) { }
