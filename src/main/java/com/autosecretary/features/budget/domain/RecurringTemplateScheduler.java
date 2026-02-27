package com.autosecretary.features.budget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the next-due date for a recurring budget template given a reference date.
 * Returns null if the template should be deactivated.
 */
public class RecurringTemplateScheduler {

    public static LocalDate computeNextDue(RecurringScheduleParams params, LocalDate referenceDate) {
        LocalDate dueDate = params.nextDue() != null ? params.nextDue() : referenceDate;

        switch (params.recurringType()) {
            case WEEKLY:
                if (params.recurringDayOfWeek() == null) return null;
                // Align to the target day of week, then skip full weeks instead of advancing day-by-day.
                dueDate = dueDate.plusDays(
                        (params.recurringDayOfWeek().getValue() - dueDate.getDayOfWeek().getValue() + 7) % 7);
                while (dueDate.isBefore(referenceDate)) {
                    dueDate = dueDate.plusWeeks(1);
                }
                break;
            case INTERVAL:
                int intervalDays = Math.max(1, params.recurringValue());
                while (dueDate.isBefore(referenceDate)) {
                    dueDate = dueDate.plusDays(intervalDays);
                }
                break;
            case MONTHLY_DAY:
                if (params.recurringValue() < 1 || params.recurringValue() > 31) {
                    return null;
                }
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(Math.min(params.recurringValue(), nextMonth.lengthOfMonth()));
                }
                break;
            case MONTHLY_LAST:
                while (dueDate.isBefore(referenceDate)) {
                    LocalDate nextMonth = dueDate.plusMonths(1);
                    dueDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                }
                break;
        }

        return dueDate;
    }

    public static List<TemplateStatusUpdate> computeStatusUpdates(
            List<RecurringScheduleParams> params, LocalDate referenceDate) {
        List<TemplateStatusUpdate> updates = new ArrayList<>();
        for (RecurringScheduleParams p : params) {
            LocalDate nextDue = computeNextDue(p, referenceDate);
            boolean active = nextDue != null;
            LocalDate dueDateToStore = active ? nextDue : p.nextDue();
            updates.add(new TemplateStatusUpdate(p.id(), dueDateToStore, active));
        }
        return updates;
    }

    private RecurringTemplateScheduler() {}
}
