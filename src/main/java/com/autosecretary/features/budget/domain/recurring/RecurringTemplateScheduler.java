package com.autosecretary.features.budget.domain.recurring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the next-due date for a recurring budget template given a reference date.
 * Returns null if the template should be deactivated.
 */
public class RecurringTemplateScheduler {

    /**
     * Computes the next-due date for a recurring template, advancing past {@code referenceDate}
     * if the stored {@code nextDue} is in the past.
     *
     * @param params        schedule parameters including the current {@code nextDue} and recurrence type
     * @param referenceDate the "today" anchor — typically {@code LocalDate.now()}
     * @return the next date on or after {@code referenceDate} when this template is due, or
     *         {@code null} if the schedule is invalid (e.g. {@code WEEKLY} with no day-of-week set
     *         or {@code MONTHLY_DAY} with an out-of-range value). A {@code null} return signals
     *         that the template should be deactivated.
     */
    public static LocalDate computeNextDue(RecurringScheduleParams params, LocalDate referenceDate) {
        LocalDate dueDate = params.nextDue() != null ? params.nextDue() : referenceDate;

        return switch (params.recurringType()) {
            case WEEKLY -> {
                if (params.recurringDayOfWeek() == null) yield null;
                // Align to the target day of week, then skip full weeks instead of advancing day-by-day.
                LocalDate aligned = dueDate.plusDays(
                        (params.recurringDayOfWeek().getValue() - dueDate.getDayOfWeek().getValue() + 7) % 7);
                while (aligned.isBefore(referenceDate)) {
                    aligned = aligned.plusWeeks(1);
                }
                yield aligned;
            }
            case INTERVAL -> {
                int intervalDays = Math.max(1, params.recurringValue());
                LocalDate d = dueDate;
                while (d.isBefore(referenceDate)) {
                    d = d.plusDays(intervalDays);
                }
                yield d;
            }
            case MONTHLY_DAY -> {
                if (params.recurringValue() < 1 || params.recurringValue() > 31) {
                    yield null;
                }
                LocalDate d = dueDate;
                while (d.isBefore(referenceDate)) {
                    LocalDate nextMonth = d.plusMonths(1);
                    d = nextMonth.withDayOfMonth(Math.min(params.recurringValue(), nextMonth.lengthOfMonth()));
                }
                yield d;
            }
            case MONTHLY_LAST -> {
                LocalDate d = dueDate;
                while (d.isBefore(referenceDate)) {
                    LocalDate nextMonth = d.plusMonths(1);
                    d = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
                }
                yield d;
            }
        };
    }

    /**
     * Batch version of {@link #computeNextDue}: computes updated scheduling state for a list of
     * templates and returns one {@link TemplateStatusUpdate} per template.
     * <p>
     * Templates for which {@code computeNextDue} returns {@code null} are marked
     * {@code active=false} (deactivated) and their stored {@code nextDue} date is preserved
     * unchanged (so it can be displayed in the UI as the last known due date).
     *
     * @param params        scheduling parameters for all active recurring templates
     * @param referenceDate the "today" anchor — typically {@code LocalDate.now()}
     * @return one status update per template; call
     *         {@code BudgetImportRepository.synchronizeRecurringTemplateState()} to persist them
     */
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
