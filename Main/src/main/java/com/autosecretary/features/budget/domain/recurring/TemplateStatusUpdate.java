package com.autosecretary.features.budget.domain.recurring;

import java.time.LocalDate;

/**
 * Carries the updated scheduling state for a single recurring template after a scheduling run.
 *
 * @param id      UUID of the recurring template to update.
 * @param nextDue The newly computed next-due date. When {@code active=false}, this retains the
 *                last known due date (unchanged) so it can be displayed in the UI.
 * @param active  {@code true} if the template should remain active (will generate future predictions);
 *                {@code false} if the template should be deactivated (e.g. invalid schedule params).
 *                Deactivated templates are kept in the database but stop generating predicted transactions.
 */
public record TemplateStatusUpdate(String id, LocalDate nextDue, boolean active) {}
