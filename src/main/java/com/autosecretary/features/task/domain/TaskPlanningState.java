package com.autosecretary.features.task.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cross-day scheduling state carried between daily scheduling iterations.
 * Tracks which tasks were scheduled on which days, enabling intelligent
 * distribution across the week.
 */
public final class TaskPlanningState {
    private final Map<String, Set<LocalDate>> scheduledDays = new HashMap<>();
    private final Map<String, Integer> totalScheduledReps = new HashMap<>();

    public void recordScheduled(String taskId, LocalDate day) {
        scheduledDays.computeIfAbsent(taskId, k -> new HashSet<>()).add(day);
        totalScheduledReps.merge(taskId, 1, Integer::sum);
    }

    public Set<LocalDate> getScheduledDays(String taskId) {
        return scheduledDays.getOrDefault(taskId, Collections.emptySet());
    }

    public int getTotalScheduledReps(String taskId) {
        return totalScheduledReps.getOrDefault(taskId, 0);
    }

    /** Returns the minimum number of days between {@code day} and any day the task is already scheduled on. */
    public int minDayDistance(String taskId, LocalDate day) {
        Set<LocalDate> days = scheduledDays.get(taskId);
        if (days == null || days.isEmpty()) return Integer.MAX_VALUE;
        int minDist = Integer.MAX_VALUE;
        for (LocalDate d : days) {
            int dist = Math.abs((int) ChronoUnit.DAYS.between(d, day));
            if (dist < minDist) minDist = dist;
        }
        return minDist;
    }
}
