package com.autosecretary.features.budget.domain.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Domain-level scheduling parameters for a recurring budget template.
 * Decouples domain scheduling logic from the Room entity.
 */
public record RecurringScheduleParams(
        String id,
        LocalDate nextDue,
        RecurringBudgetTransaction.RecurringType recurringType,
        DayOfWeek recurringDayOfWeek,
        int recurringValue
) {}
