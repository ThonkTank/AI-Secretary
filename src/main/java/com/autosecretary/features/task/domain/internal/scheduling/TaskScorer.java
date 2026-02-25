package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scoring.CompletionState;
import com.autosecretary.features.task.domain.internal.scoring.MultiDayStateSnapshot;
import com.autosecretary.features.task.domain.internal.scoring.PreferenceFitCalculator;
import com.autosecretary.features.task.domain.internal.scoring.PreferenceFitState;
import com.autosecretary.features.task.domain.internal.scoring.TaskScoringSnapshot;
import com.autosecretary.features.task.domain.internal.scoring.UrgencyCalculator;
import com.autosecretary.features.task.domain.internal.scoring.UrgencyState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
    private final Map<String, TaskScoringSnapshot> caches = new HashMap<>();
    private final UrgencyCalculator urgencyCalculator;
    private final PreferenceFitCalculator preferenceFitCalculator;

    TaskScorer(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    TaskScorer(TaskLifecycleManager lifecycleManager, double maxAgingMultiplier, double preferredStartDeviationHours) {
        this.lifecycleManager = lifecycleManager;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.urgencyCalculator = new UrgencyCalculator();
        this.preferenceFitCalculator = new PreferenceFitCalculator(preferredStartDeviationHours);
    }

    void reset() {
        caches.clear();
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
        UrgencyState urgencyState = urgencyCalculator.computeState(task, day);
        PreferenceFitState preferenceFitState = preferenceFitCalculator.computeTodayPrefSlots(task, day.getDayOfWeek());
        MultiDayStateSnapshot multiDayStateSnapshot = computeMultiDaySnapshot(task, state, day);

        int sinceLast = (int) ChronoUnit.DAYS.between(completionState.lastCompletion(), day);
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

    private void advanceTaskPeriod(Task task, LocalDate day) {
        lifecycleManager.advancePeriods(task, day);
    }

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

    private int computeMaxChildPriority(Task task) {
        int maxChildPriority = 0;
        for (Task child : task.children) {
            maxChildPriority = Math.max(maxChildPriority, child.core.priority.value);
        }
        return maxChildPriority;
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
     *       {@code PreferenceFitCalculator}'s configured deviation hours.</li>
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
        totalPrio = urgencyCalculator.applyMultiplier(totalPrio, context.task, context.snapshot.urgencyState());
        return applyAgingAndSpreadModifiers(totalPrio, context);
    }

    private boolean passesHardConstraintGate(ScoringContext context) {
        Task task = context.task;
        TaskScoringSnapshot snapshot = context.snapshot;
        if (snapshot.completionState().isComplete()) return false;
        if (snapshot.completionState().scheduledToday() >= snapshot.repsPerDay()) return false;
        if (snapshot.sinceLast() < task.core.cooldown) return false;

        MultiDayStateSnapshot multiDay = snapshot.multiDayStateSnapshot();
        if (multiDay.minDayDistance() > 0
                && multiDay.minDayDistance() < multiDay.expectedDayGap() * 0.5) {
            return false;
        }
        if (multiDay.totalScheduledReps() >= multiDay.totalRepsInPeriod()) return false;
        if (context.availableTime < task.core.minDuration) return false;
        if (task.core.progress != null && context.availableTime < task.core.progress.requiredTimePerRep()) return false;
        return !snapshot.urgencyState().isDeadlineExpired();
    }

    private int applyBasePriorityAndChildInfluence(ScoringContext context) {
        return Math.max(context.task.core.priority.value, context.snapshot.maxChildPriority());
    }

    private int applyPreferredTimeFit(int baseScore, ScoringContext context) {
        return preferenceFitCalculator.applyPreferredTimeFit(
                baseScore,
                context.snapshot.preferenceFitState(),
                context.start.toLocalTime()
        );
    }

    private int applyAgingAndSpreadModifiers(int score, ScoringContext context) {
        TaskScoringSnapshot snapshot = context.snapshot;
        int adjustedScore = (int) (score * snapshot.agingForce());
        MultiDayStateSnapshot multiDay = snapshot.multiDayStateSnapshot();
        if (multiDay.minDayDistance() > 0
                && multiDay.minDayDistance() < Integer.MAX_VALUE
                && multiDay.minDayDistance() < multiDay.expectedDayGap()) {
            double ratio = multiDay.minDayDistance() / multiDay.expectedDayGap();
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
