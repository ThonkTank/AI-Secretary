package com.autosecretary.services.taskPlanning;

import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskCore;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.database.task.TaskSlot;
import com.autosecretary.services.TaskLifecycleManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskScorer {

    private static final double MAX_AGING = 3.0;

    private final TaskLifecycleManager lifecycleManager;
    private final Map<String, ScoringCache> caches = new HashMap<>();

    public TaskScorer(TaskLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    public void reset() {
        caches.clear();
    }

    static class ScoringCache {
        int completions;
        LocalDate lastCompletion;
        int periodCompletions;
        boolean isComplete;
        int scheduledToday;

        int sinceLast;
        double remainingDays;
        double requiredDays;
        double agingForce;
        int maxChildPriority;
        List<TaskPrefSlot> todayPrefSlots;
        int repsPerDay;
        boolean deadlineExpired;
    }

    public void maintenance(Task task) {
        ScoringCache cache = new ScoringCache();
        caches.put(task.core.id, cache);
        LocalDate today = LocalDate.now();

        // 1. Täglicher Upkeep
        lifecycleManager.advancePeriods(task);

        // 2. Slot-Scan (einmalig)
        cache.completions = 0;
        cache.scheduledToday = 0;
        cache.lastCompletion = task.core.created.minusDays(1);

        TaskCore.Repetition rep = task.core.repetition;
        LocalDate periodStart = (rep != null && rep.periodStart != null) ? rep.periodStart : task.core.created;
        LocalDate periodEnd = (rep != null && rep.periodUnit != null && rep.reps > 0) ? rep.periodEnd() : null;
        cache.periodCompletions = 0;

        for (TaskSlot slot : task.slots) {
            if (slot.completed) {
                cache.completions++;
                if (slot.day.isAfter(cache.lastCompletion)) {
                    cache.lastCompletion = slot.day;
                }
                if (periodEnd != null && !slot.day.isBefore(periodStart) && slot.day.isBefore(periodEnd)) {
                    cache.periodCompletions++;
                }
            }
            if (slot.day.equals(today) && slot.scheduled) {
                cache.scheduledToday++;
            }
        }

        if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            rep.periodCompletions = cache.periodCompletions;
            cache.isComplete = cache.periodCompletions >= rep.reps;
        } else {
            cache.isComplete = cache.completions > 0;
        }

        // 3. Scoring-Konstanten vorberechnen
        cache.sinceLast = (int) ChronoUnit.DAYS.between(cache.lastCompletion, today);
        cache.remainingDays = task.remainingDays();
        cache.requiredDays = task.requiredDays();
        cache.agingForce = Math.min(1 + ((double) cache.sinceLast / 10), MAX_AGING);
        cache.repsPerDay = task.core.repsPerDay();
        cache.deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && today.isAfter(task.core.deadline);

        cache.maxChildPriority = 0;
        for (Task child : task.children) {
            cache.maxChildPriority = Math.max(cache.maxChildPriority, child.core.priority.value);
        }

        DayOfWeek todayDow = today.getDayOfWeek();
        cache.todayPrefSlots = new ArrayList<>();
        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && ps.days.contains(todayDow)) {
                cache.todayPrefSlots.add(ps);
            }
        }
    }

    public int score(Task task, LocalDateTime start, LocalDateTime end) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache == null) {
            maintenance(task);
            cache = caches.get(task.core.id);
        }

        int availableTime = (int) ChronoUnit.MINUTES.between(start, end);

        // hard constraints
        if (cache.isComplete) return 0;
        if (cache.scheduledToday >= cache.repsPerDay) return 0;
        if (cache.sinceLast < task.core.cooldown) return 0;
        if (availableTime < task.core.minDuration) return 0;
        if (task.core.progress != null && availableTime < task.core.progress.requiredTimePerRep()) return 0;
        if (cache.deadlineExpired) return 0;

        // prio
        int totalPrio = task.core.priority.value;
        if (cache.maxChildPriority > 0) {
            totalPrio = Math.max(totalPrio, cache.maxChildPriority);
        }

        // fit (todayPrefSlots bereits auf Wochentag gefiltert)
        LocalTime prefStart = null;
        long minDiff = Long.MAX_VALUE;
        for (TaskPrefSlot slot : cache.todayPrefSlots) {
            long slotDiff = Math.abs(Duration.between(start.toLocalTime(), slot.start).toMinutes());
            if (slotDiff < minDiff) {
                minDiff = slotDiff;
                prefStart = slot.start;
            }
        }

        if (prefStart != null) {
            double dif = Duration.between(start.toLocalTime(), prefStart).toMinutes() / 60.0;
            double fit = Math.max(0, 1 - Math.abs(dif / 8));
            totalPrio = (int) (totalPrio * fit);
        }

        // urgency
        double urgency;
        if (cache.remainingDays <= 0) {
            urgency = 100;
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + cache.requiredDays / cache.remainingDays;
        } else {
            urgency = 1.0;
        }
        totalPrio = (int) (totalPrio * urgency);

        // aging
        totalPrio = (int) (totalPrio * cache.agingForce);

        return totalPrio;
    }

    public void onSlotAssigned(Task task) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache != null) {
            cache.scheduledToday++;
        }
    }
}
