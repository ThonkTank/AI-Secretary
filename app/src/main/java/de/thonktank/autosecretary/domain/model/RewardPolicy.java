package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure XP and due-date rules shared by commands and read-model projections. */
public final class RewardPolicy {
    public static final int BASE_XP = 10;
    public static final int LATE_XP_PER_DAY = 5;
    public static final int MAX_SINGLE_BASE_XP = 30;

    private RewardPolicy() { }

    public static int stepXp(ComboProgress combo) {
        return multiplied(BASE_XP, combo);
    }

    public static int routineXp(int collectedStepXp, ComboProgress combo) {
        return multiplied(Math.max(0, collectedStepXp), combo);
    }

    public static int singleTaskXp(long lateDays, ComboProgress combo) {
        return multiplied(singleTaskBase(lateDays), combo);
    }

    public static int singleTaskBase(long lateDays) {
        long boundedDays = Math.max(0L, Math.min(4L, lateDays));
        return Math.min(MAX_SINGLE_BASE_XP,
                BASE_XP + Math.toIntExact(boundedDays) * LATE_XP_PER_DAY);
    }

    public static LocalDate effectiveDueDate(Task task, Occurrence occurrence) {
        if (task == null || occurrence == null) return null;
        return task.deadlineOn == null ? occurrence.scheduledOn : task.deadlineOn;
    }

    public static long lateDays(Task task, Occurrence occurrence, LocalDate today) {
        LocalDate due = effectiveDueDate(task, occurrence);
        return due == null || today == null ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(due, today));
    }

    private static int multiplied(int base, ComboProgress combo) {
        double factor = combo == null ? 1d : combo.multiplier();
        return (int) Math.round(Math.max(0, base) * factor);
    }
}
