package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

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

/**
 * Scores tasks for scheduling priority using a multi-layer multiplicative formula.
 * <p>
 * Holds a per-task {@link ScoringCache} keyed by task ID. Intended lifecycle per generation run:
 * {@link #reset()} once, then {@link #maintenance(Task)} for each task to pre-compute caches,
 * then {@link #score(Task, LocalDateTime, LocalDateTime)} for each candidate placement.
 * After a slot is assigned, call {@link #onSlotAssigned(Task)} to update the daily counter.
 */
public class TaskScorer {

    /** Upper bound for the aging multiplier — score boost caps at 3x no matter how long since last activity. */
    private static final double DEFAULT_MAX_AGING_MULTIPLIER = 3.0;
    /** Hours of deviation from preferred start at which the fit factor decays to 0.0. */
    private static final double DEFAULT_PREFERRED_START_DEVIATION_HOURS = 8.0;

    private final TaskLifecycleManager lifecycleManager;
    private final double maxAgingMultiplier;
    private final double preferredStartDeviationHours;
    private final Map<String, ScoringCache> caches = new HashMap<>();

    public TaskScorer(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    public TaskScorer(TaskLifecycleManager lifecycleManager, double maxAgingMultiplier, double preferredStartDeviationHours) {
        this.lifecycleManager = lifecycleManager;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    public void reset() {
        caches.clear();
    }

    /** Pre-computed per-task scoring data, populated by {@link #maintenance(Task)} and read by {@link #score}. */
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
        boolean hasDayConstraints;
        int repsPerDay;
        boolean deadlineExpired;
    }

    public void maintenance(Task task) {
        ScoringCache cache = new ScoringCache();
        caches.put(task.core.id, cache);
        LocalDate today = LocalDate.now();
        TaskCore.Repetition rep = task.core.repetition;

        advanceTaskPeriod(task, cache);
        scanSlots(task, cache, today);
        computeCompletionState(task, cache);
        computeDerivedMetrics(task, cache, today);
        computeTodayPrefSlots(task, cache, today.getDayOfWeek());
    }

    /**
     * Advances repetition windows before reading slot-derived counters.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code cache} are non-null.</li>
     *   <li>{@code task.core.repetition} may be null and is handled by {@link TaskLifecycleManager}.</li>
     *   <li>Must be executed before {@link #scanSlots(Task, ScoringCache, LocalDate)} so period boundaries are up-to-date.</li>
     * </ul>
     */
    private void advanceTaskPeriod(Task task, ScoringCache cache) {
        lifecycleManager.advancePeriods(task);
    }

    /**
     * Performs a single pass over all slots and fills raw counters in the cache.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code today} are non-null.</li>
     *   <li>{@code task.core.repetition} may be null, have null {@code periodUnit}, or no active period start.</li>
     *   <li>Uses half-open period range {@code [periodStart, periodEnd)} when counting period completions.</li>
     * </ul>
     */
    private void scanSlots(Task task, ScoringCache cache, LocalDate today) {
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
    }

    /**
     * Derives completion state from scanned slot counters and repetition settings.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code cache} are non-null.</li>
     *   <li>{@code cache.periodCompletions} and {@code cache.completions} were populated by {@link #scanSlots(Task, ScoringCache, LocalDate)}.</li>
     *   <li>If repetition is disabled ({@code rep == null}, {@code reps <= 0}, or null period unit), completion falls back to any historical completion.</li>
     * </ul>
     */
    private void computeCompletionState(Task task, ScoringCache cache) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            rep.periodCompletions = cache.periodCompletions;
            cache.isComplete = cache.periodCompletions >= rep.reps;
        } else {
            cache.isComplete = cache.completions > 0;
        }
    }

    /**
     * Computes scoring metrics that depend on current day, deadline, and child priorities.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code today} are non-null.</li>
     *   <li>{@code cache.lastCompletion} was initialized during slot scanning.</li>
     *   <li>Deadline is considered expired only when {@code closeOnMiss} is true, deadline exists, and {@code today} is strictly after deadline.</li>
     * </ul>
     */
    private void computeDerivedMetrics(Task task, ScoringCache cache, LocalDate today) {
        cache.sinceLast = (int) ChronoUnit.DAYS.between(cache.lastCompletion, today);
        cache.remainingDays = task.remainingDays();
        cache.requiredDays = task.requiredDays();
        // 10 = aging divisor: each 10 days of inactivity adds 1.0 to the multiplier (capped at maxAgingMultiplier)
        cache.agingForce = Math.min(1 + ((double) cache.sinceLast / 10), maxAgingMultiplier);
        cache.repsPerDay = task.core.repsPerDay();
        cache.deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && today.isAfter(task.core.deadline);

        cache.maxChildPriority = 0;
        for (Task child : task.children) {
            cache.maxChildPriority = Math.max(cache.maxChildPriority, child.core.priority.value);
        }
    }

    /**
     * Filters preferred slots to weekday-compatible entries for today's scoring.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task}, {@code cache}, and {@code dayOfWeek} are non-null.</li>
     *   <li>{@code TaskPrefSlot.days} may be null and is treated as "not applicable".</li>
     *   <li>Produces a fresh list each run to avoid leaking state across maintenance invocations.</li>
     * </ul>
     */
    private void computeTodayPrefSlots(Task task, ScoringCache cache, DayOfWeek dayOfWeek) {
        cache.todayPrefSlots = new ArrayList<>();
        cache.hasDayConstraints = false;
        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days != null && !ps.days.isEmpty()) {
                cache.hasDayConstraints = true;
                if (ps.days.contains(dayOfWeek)) {
                    cache.todayPrefSlots.add(ps);
                }
            }
        }
    }

    /**
     * Scores a task for a candidate time slot. Returns 0 if the task cannot be scheduled.
     * <p>
     * Layers applied in order, each multiplying the running total:
     * <ol>
     *   <li><b>Hard constraints</b> — returns 0 if cooldown unmet, slot too short, progress needs
     *       more time, deadline expired with closeOnMiss, already complete, or daily reps exhausted.</li>
     *   <li><b>Priority base</b> — {@code task.core.priority.value} (LOW=100 .. CRITICAL=10000).</li>
     *   <li><b>Child influence</b> — parent inherits the highest child priority when it exceeds its own.</li>
     *   <li><b>Day constraint</b> — returns 0 if prefSlots specify days but none match today.</li>
     *   <li><b>Preferred time fit</b> — linear decay from 1.0 at exact match to 0.0 at
     *       {@link #preferredStartDeviationHours} hours deviation.</li>
     *   <li><b>Urgency</b> — {@code 1 + requiredDays / remainingDays}; overdue tasks use a fixed high value.</li>
     *   <li><b>Aging</b> — {@code cache.agingForce}, pre-computed in maintenance, capped at {@link #maxAgingMultiplier}.</li>
     * </ol>
     */
    public int score(Task task, LocalDateTime start, LocalDateTime end) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache == null) {
            maintenance(task);
            cache = caches.get(task.core.id);
        }

        int availableTime = (int) ChronoUnit.MINUTES.between(start, end);
        if (!isSchedulableNow(task, cache, availableTime)) {
            return 0;
        }

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

        if (prefStart == null && cache.hasDayConstraints) {
            return 0;
        }

        if (prefStart != null) {
            double dif = Duration.between(start.toLocalTime(), prefStart).toMinutes() / 60.0;
            double fit = Math.max(0, 1 - Math.abs(dif / preferredStartDeviationHours));
            totalPrio = (int) (totalPrio * fit);
        }

        // urgency
        double urgency;
        if (cache.remainingDays <= 0) {
            urgency = 100; // fixed high urgency for overdue tasks (deadline passed, remainingDays <= 0)
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + cache.requiredDays / cache.remainingDays; // ratio-based: grows as deadline approaches
        } else {
            urgency = 1.0; // no deadline and no repetition — neutral multiplier
        }
        totalPrio = (int) (totalPrio * urgency);

        // aging
        totalPrio = (int) (totalPrio * cache.agingForce);

        return totalPrio;
    }

    private boolean isSchedulableNow(Task task, ScoringCache cache, int availableTime) {
        if (cache.isComplete) return false;
        if (cache.scheduledToday >= cache.repsPerDay) return false;
        if (cache.sinceLast < task.core.cooldown) return false;
        if (availableTime < task.core.minDuration) return false;
        if (task.core.progress != null && availableTime < task.core.progress.requiredTimePerRep()) return false;
        return !cache.deadlineExpired;
    }

    public void onSlotAssigned(Task task) {
        ScoringCache cache = caches.get(task.core.id);
        if (cache != null) {
            cache.scheduledToday++;
        }
    }
}
