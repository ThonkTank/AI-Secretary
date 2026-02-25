package com.autosecretary.features.task.domain.internal.scoring;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.domain.internal.scoring.ScoringModel.PreferenceFitState;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class PreferenceFitCalculator {
    private final double preferredStartDeviationHours;

    public PreferenceFitCalculator(double preferredStartDeviationHours) {
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    public PreferenceFitState computeTodayPrefSlots(Task task, DayOfWeek dayOfWeek) {
        List<TaskPrefSlot> todayPrefSlots = new ArrayList<>();
        boolean hasDayConstraints = false;
        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && !ps.days.isEmpty()) {
                hasDayConstraints = true;
                if (ps.days.contains(dayOfWeek)) {
                    todayPrefSlots.add(ps);
                }
            }
        }
        return new PreferenceFitState(todayPrefSlots, hasDayConstraints);
    }

    public int applyPreferredTimeFit(int baseScore, PreferenceFitState fitState, LocalTime candidateStart) {
        LocalTime prefStart = findClosestPreferredStart(fitState.todayPrefSlots(), candidateStart);
        if (prefStart == null) {
            return fitState.hasDayConstraints() ? 0 : baseScore;
        }

        double dif = Duration.between(candidateStart, prefStart).toMinutes() / 60.0;
        double fit = Math.max(0, 1 - Math.abs(dif / preferredStartDeviationHours));
        return (int) (baseScore * fit);
    }

    private LocalTime findClosestPreferredStart(List<TaskPrefSlot> preferredSlots, LocalTime candidateStart) {
        LocalTime preferredStart = null;
        long minDiff = Long.MAX_VALUE;
        for (TaskPrefSlot slot : preferredSlots) {
            long slotDiff = Math.abs(Duration.between(candidateStart, slot.start).toMinutes());
            if (slotDiff < minDiff) {
                minDiff = slotDiff;
                preferredStart = slot.start;
            }
        }
        return preferredStart;
    }
}
