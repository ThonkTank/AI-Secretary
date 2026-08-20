package de.thonktank.autosecretary;

import java.time.DayOfWeek;
import java.time.LocalDate;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;

/** Pure schedule rules; all dates are passed in so recurrence behaviour is deterministic in tests. */
public final class ScheduleCalculator {
    private ScheduleCalculator() { }
    /** Returns the next calendar occurrence after the supplied planned date. */
    public static LocalDate nextDue(Task task, LocalDate plannedOn) {
        if (task.recurrence == Recurrence.ONCE) return null;
        if (task.boundKind == TaskBoundKind.N_TIMES
                && (task.remainingCount == null || task.remainingCount <= 0)) return null;
        LocalDate candidate = null;
        if (task.recurrence == Recurrence.DAILY) candidate = plannedOn.plusDays(1);
        if (task.recurrence == Recurrence.INTERVAL)
            candidate = plannedOn.plusDays(Math.max(1, task.intervalDays));
        if (task.recurrence == Recurrence.WEEKDAYS)
            candidate = nextSelectedWeekday(task.weekdayMask, plannedOn.plusDays(1));
        return withinBound(task, candidate) ? candidate : null;
    }
    public static boolean isDue(Task task, LocalDate today) {
        return task.nextDueOn != null && !task.nextDueOn.isAfter(today);
    }
    public static boolean completedOnTime(String scheduledOn, LocalDate completedOn) {
        return LocalDate.parse(scheduledOn).equals(completedOn);
    }
    public static int weekdayMask(boolean[] selected) {
        int mask = 0; for (int i = 0; i < selected.length && i < 7; i++) if (selected[i]) mask |= 1 << i; return mask;
    }
    public static boolean hasWeekday(int mask) { return mask != 0; }
    public static LocalDate firstDue(Recurrence recurrence, int weekdayMask, LocalDate today) {
        if (recurrence == Recurrence.WEEKDAYS) return nextSelectedWeekday(weekdayMask, today);
        return today;
    }
    public static boolean appliesOn(int weekdayMask, LocalDate date) {
        if (weekdayMask == 0) return true;
        int bit = 1 << (date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        return (weekdayMask & bit) != 0;
    }
    public static boolean withinBound(Task task, LocalDate date) {
        if (date == null) return false;
        if (task.boundKind == TaskBoundKind.UNTIL_DATE
                || task.boundKind == TaskBoundKind.FOR_WEEKS)
            return task.boundUntilOn != null && !date.isAfter(task.boundUntilOn);
        if (task.boundKind == TaskBoundKind.N_TIMES)
            return task.remainingCount != null && task.remainingCount > 0;
        return true;
    }
    public static LocalDate nextSelectedWeekday(int mask, LocalDate start) {
        if (mask == 0) throw new IllegalArgumentException("Wochentags-Aufgaben benötigen mindestens einen Tag.");
        for (int i = 0; i < 7; i++) {
            LocalDate candidate = start.plusDays(i);
            int bit = 1 << (candidate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            if ((mask & bit) != 0) return candidate;
        }
        throw new IllegalStateException("Unreachable");
    }
}
