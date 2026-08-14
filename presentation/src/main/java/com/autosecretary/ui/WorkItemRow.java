package com.autosecretary.ui;

import java.time.LocalDate;

public record WorkItemRow(
        String id,
        boolean routine,
        String title,
        String group,
        String metadata,
        boolean open,
        boolean completed,
        int completedSteps,
        int totalSteps,
        LocalDate completedAt) { }
