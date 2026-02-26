package com.autosecretary.features.budget.domain.internal;

import com.autosecretary.features.budget.data.importing.BudgetRecurringTemplateEntity;

import java.time.LocalDate;

/**
 * Computes the next-due date for a recurring budget template given a reference date.
 * Returns null if the template should be deactivated.
 */
public class RecurringTemplateScheduler {

    public static LocalDate computeNextDue(BudgetRecurringTemplateEntity template, LocalDate referenceDate) {
        LocalDate dueDate = template.nextDue != null ? template.nextDue : referenceDate;

        if ("WEEKLY".equals(template.recurringType) && template.recurringDayOfWeek != null) {
            while (dueDate.isBefore(referenceDate) || dueDate.getDayOfWeek() != template.recurringDayOfWeek) {
                dueDate = dueDate.plusDays(1);
            }
        } else if ("INTERVAL".equals(template.recurringType)) {
            int intervalDays = Math.max(1, template.recurringValue);
            while (dueDate.isBefore(referenceDate)) {
                dueDate = dueDate.plusDays(intervalDays);
            }
        } else if ("MONTHLY_DAY".equals(template.recurringType)) {
            if (template.recurringValue < 1 || template.recurringValue > 31) {
                return null;
            }
            while (dueDate.isBefore(referenceDate)) {
                LocalDate nextMonth = dueDate.plusMonths(1);
                dueDate = nextMonth.withDayOfMonth(Math.min(template.recurringValue, nextMonth.lengthOfMonth()));
            }
        } else if ("MONTHLY_LAST".equals(template.recurringType)) {
            while (dueDate.isBefore(referenceDate)) {
                LocalDate nextMonth = dueDate.plusMonths(1);
                dueDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
            }
        } else if (template.nextDue == null) {
            return null;
        }

        return dueDate;
    }

    private RecurringTemplateScheduler() {}
}
