package com.autosecretary.features.budget.domain.internal;

import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.TemplateStatusUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                int intervalDays = Math.max(1, template.schedulingParam);
                while (dueDate.isBefore(referenceDate)) {
                    dueDate = dueDate.plusDays(intervalDays);
                }
                break;
            case MONTHLY_DAY:
                if (template.schedulingParam < 1 || template.schedulingParam > 31) {
                    return null;
                }
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(Math.min(template.schedulingParam, nextMonth.lengthOfMonth()));
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

    public static List<TemplateStatusUpdate> computeStatusUpdates(
            List<BudgetRecurringTemplateEntity> templates, LocalDate referenceDate) {
        List<TemplateStatusUpdate> updates = new ArrayList<>();
        for (BudgetRecurringTemplateEntity template : templates) {
            LocalDate nextDue = computeNextDue(template, referenceDate);
            boolean active = nextDue != null;
            LocalDate dueDateToStore = active ? nextDue : template.nextDue;
            updates.add(new TemplateStatusUpdate(template.id, dueDateToStore, active));
        }
        return updates;
    }

    private RecurringTemplateScheduler() {}
}
