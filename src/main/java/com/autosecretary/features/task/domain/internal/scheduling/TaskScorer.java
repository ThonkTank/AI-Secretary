package com.autosecretary.features.task.domain.internal.scheduling;

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
 * Holds a per-task {@link TaskScoringSnapshot} keyed by task ID. Intended lifecycle per generation run:
 * {@link #reset()} once, then {@link #maintenance(Task)} for each task to pre-compute caches,
 * then {@link #score(Task, LocalDateTime, LocalDateTime)} for each candidate placement.
 * After a slot is assigned, call {@link #onSlotAssigned(Task)} to update the daily counter.
 */
final class TaskScorer {

    /** Upper bound for the aging multiplier — score boost caps at 3x no matter how long since last activity. */
    private static final double DEFAULT_MAX_AGING_MULTIPLIER = 3.0;
    /** Hours of deviation from preferred start at which the fit factor decays to 0.0. */
    private static final double DEFAULT_PREFERRED_START_DEVIATION_HOURS = 8.0;

    private final TaskLifecycleManager lifecycleManager;
    private final double maxAgingMultiplier;
    private final double preferredStartDeviationHours;
    private final Map<String, TaskScoringSnapshot> caches = new HashMap<>();

    TaskScorer(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    TaskScorer(TaskLifecycleManager lifecycleManager, double maxAgingMultiplier, double preferredStartDeviationHours) {
        this.lifecycleManager = lifecycleManager;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    void reset() {
        caches.clear();
    }

    /** Immutable per-task scoring snapshot, populated by {@link #maintenance(Task)} and read by {@link #score}. */
    static final class TaskScoringSnapshot {
        final CompletionState completionState;
        final UrgencyState urgencyState;
        final PreferenceFitState preferenceFitState;
        final MultiDayStateSnapshot multiDayStateSnapshot;
        final int sinceLast;
        final double agingForce;
        final int repsPerDay;
        final int maxChildPriority;

        TaskScoringSnapshot(CompletionState completionState,
                            UrgencyState urgencyState,
                            PreferenceFitState preferenceFitState,
                            MultiDayStateSnapshot multiDayStateSnapshot,
                            int sinceLast,
                            double agingForce,
                            int repsPerDay,
                            int maxChildPriority) {
            this.completionState = completionState;
            this.urgencyState = urgencyState;
            this.preferenceFitState = preferenceFitState;
            this.multiDayStateSnapshot = multiDayStateSnapshot;
            this.sinceLast = sinceLast;
            this.agingForce = agingForce;
            this.repsPerDay = repsPerDay;
            this.maxChildPriority = maxChildPriority;
        }

        TaskScoringSnapshot withIncrementedScheduledToday() {
            return new TaskScoringSnapshot(
                    completionState.withIncrementedScheduledToday(),
                    urgencyState,
                    preferenceFitState,
                    multiDayStateSnapshot,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority
            );
        }
    }

    static final class CompletionState {
        final int completions;
        final LocalDate lastCompletion;
        final int periodCompletions;
        final boolean isComplete;
        final int scheduledToday;

        CompletionState(int completions, LocalDate lastCompletion, int periodCompletions, boolean isComplete, int scheduledToday) {
            this.completions = completions;
            this.lastCompletion = lastCompletion;
            this.periodCompletions = periodCompletions;
            this.isComplete = isComplete;
            this.scheduledToday = scheduledToday;
        }

        CompletionState withIncrementedScheduledToday() {
            return new CompletionState(completions, lastCompletion, periodCompletions, isComplete, scheduledToday + 1);
        }
    }

    static final class UrgencyState {
        final double remainingDays;
        final double requiredDays;
        final boolean deadlineExpired;

        UrgencyState(double remainingDays, double requiredDays, boolean deadlineExpired) {
            this.remainingDays = remainingDays;
            this.requiredDays = requiredDays;
            this.deadlineExpired = deadlineExpired;
        }
    }

    static final class PreferenceFitState {
        final List<TaskPrefSlot> todayPrefSlots;
        final boolean hasDayConstraints;

        PreferenceFitState(List<TaskPrefSlot> todayPrefSlots, boolean hasDayConstraints) {
            this.todayPrefSlots = List.copyOf(todayPrefSlots);
            this.hasDayConstraints = hasDayConstraints;
        }
    }

    static final class MultiDayStateSnapshot {
        final int totalScheduledReps;
        final int totalRepsInPeriod;
        final int minDayDistance;
        final double expectedDayGap;

        MultiDayStateSnapshot(int totalScheduledReps, int totalRepsInPeriod, int minDayDistance, double expectedDayGap) {
            this.totalScheduledReps = totalScheduledReps;
            this.totalRepsInPeriod = totalRepsInPeriod;
            this.minDayDistance = minDayDistance;
            this.expectedDayGap = expectedDayGap;
        }
    }

    static final class SlotScanResult {
        final int completions;
        final LocalDate lastCompletion;
        final int periodCompletions;
        final int scheduledToday;

        SlotScanResult(int completions, LocalDate lastCompletion, int periodCompletions, int scheduledToday) {
            this.completions = completions;
            this.lastCompletion = lastCompletion;
            this.periodCompletions = periodCompletions;
            this.scheduledToday = scheduledToday;
        }
    }

    void maintenance(Task task) {
        maintenance(task, LocalDate.now(), new MultiDayState());
    }

    void maintenance(Task task, LocalDate day, MultiDayState state) {
        advanceTaskPeriod(task, day);
        SlotScanResult slotScanResult = scanSlots(task, day);
        CompletionState completionState = computeCompletionState(task, slotScanResult);
        UrgencyState urgencyState = computeUrgencyState(task, day);
        PreferenceFitState preferenceFitState = computeTodayPrefSlots(task, day.getDayOfWeek());
        MultiDayStateSnapshot multiDayStateSnapshot = computeMultiDaySnapshot(task, state, day);

        int sinceLast = (int) ChronoUnit.DAYS.between(completionState.lastCompletion, day);
        double agingForce = Math.min(1 + ((double) sinceLast / 10), maxAgingMultiplier);
        int maxChildPriority = computeMaxChildPriority(task);

        TaskScoringSnapshot snapshot = new TaskScoringSnapshot(
                completionState,
                urgencyState,
                preferenceFitState,
                multiDayStateSnapshot,
                sinceLast,
                agingForce,
                task.core.repsPerDay(),
                maxChildPriority
        );
        caches.put(task.core.id, snapshot);
    }

    /**
     * Advances repetition windows before reading slot-derived counters.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} is non-null.</li>
     *   <li>{@code task.core.repetition} may be null and is handled by {@link TaskLifecycleManager}.</li>
     *   <li>Must be executed before {@link #scanSlots(Task, LocalDate)} so period boundaries are up-to-date.</li>
     * </ul>
     */
    private void advanceTaskPeriod(Task task, LocalDate day) {
        lifecycleManager.advancePeriods(task, day);
    }

    /**
     * Performs a single pass over all slots and fills raw counters in the cache.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code today} are non-null.</li>
     *   <li>{@code task.core.repetition} may be null, have null {@code periodUnit}, or no active period start.</li>
     *   <li>Uses half-open period range {@code [periodStart, periodEnd)} when counting period completions.</li>
     * </ul>
     */
    private SlotScanResult scanSlots(Task task, LocalDate today) {
        int completions = 0;
        int scheduledToday = 0;
        LocalDate lastCompletion = task.core.created.minusDays(1);

        TaskCore.Repetition rep = task.core.repetition;
        LocalDate periodStart = (rep != null && rep.periodStart != null) ? rep.periodStart : task.core.created;
        LocalDate periodEnd = (rep != null && rep.periodUnit != null && rep.reps > 0) ? rep.periodEnd() : null;
        int periodCompletions = 0;

        for (TaskSlot slot : task.slots) {
            if (slot.completed) {
                completions++;
                if (slot.day.isAfter(lastCompletion)) {
                    lastCompletion = slot.day;
                }
                if (periodEnd != null && !slot.day.isBefore(periodStart) && slot.day.isBefore(periodEnd)) {
                    periodCompletions++;
                }
            }
            if (slot.day.equals(today) && slot.scheduled) {
                scheduledToday++;
            }
        }

        return new SlotScanResult(completions, lastCompletion, periodCompletions, scheduledToday);
    }

    /**
     * Derives completion state from scanned slot counters and repetition settings.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} is non-null.</li>
     *   <li>{@code slotScanResult.periodCompletions} and {@code slotScanResult.completions} were populated by {@link #scanSlots(Task, LocalDate)}.</li>
     *   <li>If repetition is disabled ({@code rep == null}, {@code reps <= 0}, or null period unit), completion falls back to any historical completion.</li>
     * </ul>
     */
    private CompletionState computeCompletionState(Task task, SlotScanResult slotScanResult) {
        TaskCore.Repetition rep = task.core.repetition;
        boolean isComplete;
        if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            rep.periodCompletions = slotScanResult.periodCompletions;
            isComplete = slotScanResult.periodCompletions >= rep.reps;
        } else {
            isComplete = slotScanResult.completions > 0;
        }
        return new CompletionState(
                slotScanResult.completions,
                slotScanResult.lastCompletion,
                slotScanResult.periodCompletions,
                isComplete,
                slotScanResult.scheduledToday
        );
    }

    /**
     * Computes scoring metrics that depend on current day, deadline, and child priorities.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code today} are non-null.</li>
     *   <li>Uses task deadline/repetition windows relative to the provided scheduling day.</li>
     *   <li>Deadline is considered expired only when {@code closeOnMiss} is true, deadline exists, and {@code today} is strictly after deadline.</li>
     * </ul>
     */
    private UrgencyState computeUrgencyState(Task task, LocalDate day) {
        // Compute remainingDays relative to scheduling day (not LocalDate.now())
        TaskCore.Repetition rep = task.core.repetition;
        double remainingDays;
        if (task.core.deadline != null) {
            remainingDays = (double) ChronoUnit.DAYS.between(day, task.core.deadline);
        } else if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            LocalDate periodEnd = rep.periodEnd();
            remainingDays = periodEnd != null
                    ? (double) ChronoUnit.DAYS.between(day, periodEnd)
                    : rep.periodInDays();
        } else {
            remainingDays = 1;
        }
        double requiredDays = task.requiredDays();
        boolean deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && day.isAfter(task.core.deadline);
        return new UrgencyState(remainingDays, requiredDays, deadlineExpired);
    }

    private int computeMaxChildPriority(Task task) {
        int maxChildPriority = 0;
        for (Task child : task.children) {
            maxChildPriority = Math.max(maxChildPriority, child.core.priority.value);
        }
        return maxChildPriority;
    }

    /**
     * Filters preferred slots to weekday-compatible entries for today's scoring.
     * <p>
     * Invariant assumptions:
     * <ul>
     *   <li>{@code task} and {@code dayOfWeek} are non-null.</li>
     *   <li>{@code TaskPrefSlot.days} may be null and is treated as "not applicable".</li>
     *   <li>Produces a fresh list each run to avoid leaking state across maintenance invocations.</li>
     * </ul>
     */
    private PreferenceFitState computeTodayPrefSlots(Task task, DayOfWeek dayOfWeek) {
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

    private MultiDayStateSnapshot computeMultiDaySnapshot(Task task, MultiDayState state, LocalDate day) {
        TaskCore.Repetition rep = task.core.repetition;
        int totalScheduledReps = state.getTotalScheduledReps(task.core.id);
        int minDayDistance = state.minDayDistance(task.core.id, day);
        int totalRepsInPeriod;
        double expectedDayGap;

        if (rep != null && rep.reps > 0) {
            int periodsInWindow = Math.max(1, (int) Math.ceil(7.0 / rep.periodInDays()));
            totalRepsInPeriod = rep.reps * periodsInWindow;
            expectedDayGap = (double) rep.periodInDays() / rep.reps;
        } else {
            totalRepsInPeriod = 1;
            expectedDayGap = 7;
        }

        return new MultiDayStateSnapshot(totalScheduledReps, totalRepsInPeriod, minDayDistance, expectedDayGap);
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
     *   <li><b>Aging</b> — snapshot aging force, pre-computed in maintenance, capped at {@link #maxAgingMultiplier}.</li>
     * </ol>
     */
    int score(Task task, LocalDateTime start, LocalDateTime end) {
        TaskScoringSnapshot snapshot = caches.get(task.core.id);
        if (snapshot == null) {
            maintenance(task);
            snapshot = caches.get(task.core.id);
        }

        ScoringContext context = new ScoringContext(task, snapshot, start, end);
        if (!passesHardConstraintGate(context)) {
            return 0;
        }

        int totalPrio = applyBasePriorityAndChildInfluence(context);
        totalPrio = applyPreferredTimeFit(totalPrio, context);
        if (totalPrio <= 0) {
            return 0;
        }
        totalPrio = applyUrgencyMultiplier(totalPrio, context);
        return applyAgingAndSpreadModifiers(totalPrio, context);
    }

    private boolean passesHardConstraintGate(ScoringContext context) {
        Task task = context.task;
        TaskScoringSnapshot snapshot = context.snapshot;
        if (snapshot.completionState.isComplete) return false;
        if (snapshot.completionState.scheduledToday >= snapshot.repsPerDay) return false;
        if (snapshot.sinceLast < task.core.cooldown) return false;
        // Multi-day spacing: block if scheduled more frequently than expected
        if (snapshot.multiDayStateSnapshot.minDayDistance > 0
                && snapshot.multiDayStateSnapshot.minDayDistance < snapshot.multiDayStateSnapshot.expectedDayGap * 0.5) {
            return false;
        }
        // Period reps exhausted across all days
        if (snapshot.multiDayStateSnapshot.totalScheduledReps >= snapshot.multiDayStateSnapshot.totalRepsInPeriod) return false;
        if (context.availableTime < task.core.minDuration) return false;
        if (task.core.progress != null && context.availableTime < task.core.progress.requiredTimePerRep()) return false;
        return !snapshot.urgencyState.deadlineExpired;
    }

    private int applyBasePriorityAndChildInfluence(ScoringContext context) {
        return Math.max(context.task.core.priority.value, context.snapshot.maxChildPriority);
    }

    private int applyPreferredTimeFit(int baseScore, ScoringContext context) {
        PreferenceFitState fitState = context.snapshot.preferenceFitState;

        LocalTime prefStart = findClosestPreferredStart(fitState.todayPrefSlots, context.start.toLocalTime());
        if (prefStart == null) {
            return fitState.hasDayConstraints ? 0 : baseScore;
        }

        double dif = Duration.between(context.start.toLocalTime(), prefStart).toMinutes() / 60.0;
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

    private int applyUrgencyMultiplier(int score, ScoringContext context) {
        UrgencyState urgencyState = context.snapshot.urgencyState;
        double urgency;
        if (urgencyState.remainingDays <= 0) {
            urgency = 100;
        } else if (context.task.core.deadline != null || (context.task.core.repetition != null && context.task.core.repetition.reps > 0)) {
            urgency = 1.0 + urgencyState.requiredDays / urgencyState.remainingDays;
        } else {
            urgency = 1.0;
        }
        return (int) (score * urgency);
    }

    private int applyAgingAndSpreadModifiers(int score, ScoringContext context) {
        TaskScoringSnapshot snapshot = context.snapshot;
        int adjustedScore = (int) (score * snapshot.agingForce);
        if (snapshot.multiDayStateSnapshot.minDayDistance > 0
                && snapshot.multiDayStateSnapshot.minDayDistance < Integer.MAX_VALUE
                && snapshot.multiDayStateSnapshot.minDayDistance < snapshot.multiDayStateSnapshot.expectedDayGap) {
            double ratio = snapshot.multiDayStateSnapshot.minDayDistance / snapshot.multiDayStateSnapshot.expectedDayGap;
            double spread = Math.min(1.0, 0.1 + ratio * 0.9);
            adjustedScore = (int) (adjustedScore * spread);
        }
        return adjustedScore;
    }

    void onSlotAssigned(Task task) {
        TaskScoringSnapshot snapshot = caches.get(task.core.id);
        if (snapshot != null) {
            caches.put(task.core.id, snapshot.withIncrementedScheduledToday());
        }
    }

    static final class ScoringContext {
        final Task task;
        final TaskScoringSnapshot snapshot;
        final LocalDateTime start;
        final int availableTime;

        ScoringContext(Task task, TaskScoringSnapshot snapshot, LocalDateTime start, LocalDateTime end) {
            this.task = task;
            this.snapshot = snapshot;
            this.start = start;
            this.availableTime = (int) ChronoUnit.MINUTES.between(start, end);
        }
    }
}
