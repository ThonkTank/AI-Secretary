package com.autosecretary.features.budget.domain.internal;

import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.LocalDate;

/**
 * Computes the next-due date for a recurring budget template given a reference date.
 * Returns null if the template should be deactivated.
 */
public class RecurringTemplateScheduler {

    public static LocalDate computeNextDue(BudgetRecurringTemplateEntity template, LocalDate referenceDate) {
        LocalDate dueDate = template.nextDue != null ? template.nextDue : referenceDate;

        switch (template.recurringType) {
            case WEEKLY:
                if (template.recurringDayOfWeek == null) break;
                while (dueDate.isBefore(referenceDate) || dueDate.getDayOfWeek() != template.recurringDayOfWeek) {
                    dueDate = dueDate.plusDays(1);
                }
                break;
            case INTERVAL:
                int intervalDays = Math.max(1, template.recurringValue);
                while (dueDate.isBefore(referenceDate)) {
                    dueDate = dueDate.plusDays(intervalDays);
                }
                break;
            case MONTHLY_DAY:
                if (template.recurringValue < 1 || template.recurringValue > 31) {
                    return null;
                }
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(Math.min(template.recurringValue, nextMonth.lengthOfMonth()));
                }
                break;
            case MONTHLY_LAST:
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                }
                break;
            default:
                if (template.nextDue == null) {
                    return null;
                }
                break;
        }

        return dueDate;
    }

    private RecurringTemplateScheduler() {}
}
