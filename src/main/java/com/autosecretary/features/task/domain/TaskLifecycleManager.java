package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public class TaskLifecycleManager {

    private static final double DEFAULT_PREF_SLOT_EMA_ALPHA = 0.2;

    private final double prefSlotEmaAlpha;

    public TaskLifecycleManager() {
        this(DEFAULT_PREF_SLOT_EMA_ALPHA);
    }

    public TaskLifecycleManager(double prefSlotEmaAlpha) {
        this.prefSlotEmaAlpha = prefSlotEmaAlpha;
    }

    public void advancePeriods(Task task) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep == null || rep.reps <= 0 || rep.periodUnit == null) return;
        if (rep.periodStart == null) rep.periodStart = task.core.created;

        LocalDate now = LocalDate.now();
        int periodDays = rep.periodInDays();
        if (periodDays <= 0) return;
        if (now.isBefore(rep.periodEnd())) return;

        // Abgelaufene Periode bewerten
        boolean goalMet = rep.periodCompletions >= rep.reps;
        if (!goalMet && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }

        // Bulk-Jump zur aktuellen Periodengrenze
        long daysSinceStart = ChronoUnit.DAYS.between(rep.periodStart, now);
        long fullPeriods = daysSinceStart / periodDays;
        rep.periodStart = rep.periodStart.plusDays(fullPeriods * periodDays);
        rep.periodCompletions = 0;

        // Uebersprungene leere Perioden brechen auch den Streak
        if (fullPeriods > 1 && task.core.history.currentStreak > 0) {
            task.core.history.nrStreaks++;
            task.core.history.currentStreak = 0;
        }
    }

    public void updateStreak(Task task, TaskSlot completedSlot) {
        if (task.core.repetition == null || task.core.repetition.reps <= 0
                || task.core.repetition.periodUnit == null) return;

        advancePeriods(task);

        LocalDate ps = task.core.repetition.periodStart != null ? task.core.repetition.periodStart : task.core.created;
        LocalDate pe = task.core.repetition.periodEnd();
        int count = 0;
        for (TaskSlot s : task.slots) {
            if (s.completed && pe != null && !s.day.isBefore(ps) && s.day.isBefore(pe)) {
                count++;
            }
        }
        task.core.repetition.periodCompletions = count;

        if (count == task.core.repetition.reps) {
            task.core.history.currentStreak++;
        }
    }

    public void adaptPrefSlot(Task task, TaskSlot slot) {
        if (slot.realStart == null || task.prefSlots == null || task.prefSlots.isEmpty()) return;

        DayOfWeek today = slot.day.getDayOfWeek();
        TaskPrefSlot bestMatch = null;
        long bestDiff = Long.MAX_VALUE;

        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && ps.days.contains(today) && ps.start != null) {
                long diff = Math.abs(Duration.between(ps.start, slot.realStart).toMinutes());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestMatch = ps;
                }
            }
        }

        if (bestMatch == null) return;

        long prefMinutes = bestMatch.start.toSecondOfDay() / 60;
        long actualMinutes = slot.realStart.toSecondOfDay() / 60;
        long newMinutes = Math.round(prefMinutes * (1 - prefSlotEmaAlpha) + actualMinutes * prefSlotEmaAlpha);
        newMinutes = Math.round(newMinutes / 5.0) * 5;

        bestMatch.start = LocalTime.of((int) (newMinutes / 60), (int) (newMinutes % 60));
    }
}
