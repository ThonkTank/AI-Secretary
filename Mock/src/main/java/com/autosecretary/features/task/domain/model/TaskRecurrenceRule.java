package com.autosecretary.features.task.domain.model;

import java.time.LocalDate;

/**
 * Repeat policy attached to a task definition.
 *
 * <p>Examples:
 * <ul>
 *   <li>Not repeating: {@code isRepeating=false}</li>
 *   <li>Every day: {@code isRepeating=true, everyNUnits=1, unit=DAY}</li>
 *   <li>Every 2 weeks in a bounded window.</li>
 * </ul>
 */
public record TaskRecurrenceRule(boolean isRepeating,
                                 Integer everyNUnits,
                                 RecurrenceUnit unit,
                                 LocalDate windowStartDay,
                                 LocalDate windowEndDay) {
}
