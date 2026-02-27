package com.autosecretary.features.budget.domain.recurring;

import java.time.LocalDate;

/** Carries the updated scheduling state for a single recurring template. */
public record TemplateStatusUpdate(String id, LocalDate nextDue, boolean active) {}
