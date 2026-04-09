package com.autosecretary.features.budget.domain.recurring;

/**
 * Classifies the recurrence pattern of a recurring budget transaction template.
 *
 * <p>Used by {@link RecurringScheduleParams}, {@link RecurringSuggestion}, and the
 * pattern detection pipeline to describe how often a template repeats:
 * <ul>
 *   <li>{@code MONTHLY_DAY} — same day of month each month (e.g., rent on the 1st)</li>
 *   <li>{@code MONTHLY_LAST} — last day of each month (e.g., end-of-month utility bill)</li>
 *   <li>{@code WEEKLY} — same day of week each week (e.g., weekly gym payment)</li>
 *   <li>{@code INTERVAL} — fixed interval in days (e.g., every 14 days)</li>
 * </ul>
 */
public enum RecurringType {
    MONTHLY_DAY,
    MONTHLY_LAST,
    WEEKLY,
    INTERVAL
}
