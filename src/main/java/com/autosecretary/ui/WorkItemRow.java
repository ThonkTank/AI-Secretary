package com.autosecretary.ui;

public record WorkItemRow(
        String id,
        boolean routine,
        String title,
        String group,
        String metadata,
        boolean open,
        boolean completed,
        int completedSteps,
        int totalSteps) { }
