package de.thonktank.autosecretary;

import java.time.DayOfWeek;
import java.time.LocalDate;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;

/** Pure schedule rules; all dates are passed in so recurrence behaviour is deterministic in tests. */
public final class ScheduleCalculator {
    private ScheduleCalculator() { }
    public static LocalDate nextDue(Task task, LocalDate completedOn) {
        if (task.recurrence == Recurrence.ONCE) return null;
        if (task.recurrence == Recurrence.DAILY) return completedOn.plusDays(1);
        if (task.recurrence == Recurrence.INTERVAL) return completedOn.plusDays(Math.max(1, task.intervalDays));
        if (task.recurrence == Recurrence.WEEKDAYS) return nextSelectedWeekday(task.weekdayMask, completedOn.plusDays(1));
        return null;
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
    private static LocalDate nextSelectedWeekday(int mask, LocalDate start) {
        if (mask == 0) throw new IllegalArgumentException("Wochentags-Aufgaben benötigen mindestens einen Tag.");
        for (int i = 0; i < 7; i++) {
            LocalDate candidate = start.plusDays(i);
            int bit = 1 << (candidate.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            if ((mask & bit) != 0) return candidate;
        }
        throw new IllegalStateException("Unreachable");
    }
}
