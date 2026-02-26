package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStat;
import com.autosecretary.features.task.domain.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskPlanningState;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
    private static final double FOLLOW_UP_MULTIPLIER_PER_WEIGHT = 0.08;
    private static final double FOLLOW_UP_ADDITIVE_PER_WEIGHT = 120.0;
    private static final double FOLLOW_UP_MULTIPLIER_CAP = 1.6;
    private static final double FOLLOW_UP_ADDITIVE_CAP = 1800.0;

    private final TaskLifecycleManager lifecycleManager;
    private final double maxAgingMultiplier;
    private final double preferredStartDeviationHours;
    private final Map<String, TaskScoringSnapshot> caches = new HashMap<>();
    private final Map<String, Map<String, TaskTransitionStat>> transitionStats = new HashMap<>();
    private final Consumer<String> logger;
    private final TaskBudgetEligibilityService budgetEligibilityService;

    TaskScorer(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, null, null, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    TaskScorer(TaskLifecycleManager lifecycleManager, Consumer<String> logger) {
        this(lifecycleManager, logger, null, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    TaskScorer(TaskLifecycleManager lifecycleManager, Consumer<String> logger, TaskBudgetEligibilityService budgetEligibilityService) {
        this(lifecycleManager, logger, budgetEligibilityService, DEFAULT_MAX_AGING_MULTIPLIER, DEFAULT_PREFERRED_START_DEVIATION_HOURS);
    }

    TaskScorer(TaskLifecycleManager lifecycleManager,
               Consumer<String> logger,
               TaskBudgetEligibilityService budgetEligibilityService,
               double maxAgingMultiplier,
               double preferredStartDeviationHours) {
        this.lifecycleManager = lifecycleManager;
        this.logger = logger;
        this.budgetEligibilityService = budgetEligibilityService;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    void reset() {
        caches.clear();
    }

    void setTransitionStats(List<TaskTransitionStat> stats) {
        transitionStats.clear();
        if (stats == null) {
            return;
        }
        for (TaskTransitionStat stat : stats) {
            transitionStats.computeIfAbsent(stat.fromTaskId, key -> new HashMap<>())
                    .put(stat.toTaskId, stat);
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

    record CompletionState(int completions,
                           LocalDate lastCompletion,
                           int periodCompletions,
                           boolean isComplete,
                           int scheduledToday) {
        CompletionState withIncrementedScheduledToday() {
            return new CompletionState(completions, lastCompletion, periodCompletions, isComplete, scheduledToday + 1);
        }
    }

    record UrgencyState(double remainingDays,
                        double requiredDays,
                        boolean isDeadlineExpired) {
    }

    record PreferenceFitState(List<TaskPrefSlot> todayPrefSlots,
                              boolean hasDayConstraints,
                              Set<String> consumedPrefSlotIds) {
        PreferenceFitState {
            todayPrefSlots = List.copyOf(todayPrefSlots);
            consumedPrefSlotIds = Set.copyOf(consumedPrefSlotIds);
        }

        PreferenceFitState(List<TaskPrefSlot> todayPrefSlots, boolean hasDayConstraints) {
            this(todayPrefSlots, hasDayConstraints, Set.of());
        }

        PreferenceFitState withConsumedPrefSlot(String prefSlotId) {
            Set<String> newConsumed = new HashSet<>(consumedPrefSlotIds);
            newConsumed.add(prefSlotId);
            return new PreferenceFitState(todayPrefSlots, hasDayConstraints, newConsumed);
        }
    }

    record MultiDayStateSnapshot(int totalScheduledReps,
                                 int totalRepsInPeriod,
                                 int minDayDistance,
                                 double expectedDayGap) {
    }

    record TaskScoringSnapshot(CompletionState completionState,
                               UrgencyState urgencyState,
                               PreferenceFitState preferenceFitState,
                               MultiDayStateSnapshot multiDayStateSnapshot,
                               int sinceLast,
                               double agingForce,
                               int repsPerDay,
                               int maxChildPriority) {
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

        // Assigning a slot always schedules one repetition for today; when it matches a preference slot,
        // we also mark that preference slot as consumed to avoid reusing it.
        TaskScoringSnapshot withAssignedPrefSlot(String prefSlotId) {
            return new TaskScoringSnapshot(
                    completionState.withIncrementedScheduledToday(),
                    urgencyState,
                    preferenceFitState.withConsumedPrefSlot(prefSlotId),
                    multiDayStateSnapshot,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority
            );
        }
    }

    void maintenance(Task task) {
        maintenance(task, LocalDate.now(), new TaskPlanningState());
    }

    void maintenance(Task task, LocalDate day, TaskPlanningState state) {
        advanceTaskPeriod(task, day);
        SlotScanResult slotScanResult = scanSlots(task, day);
        CompletionState completionState = computeCompletionState(task, slotScanResult);
        UrgencyState urgencyState = computeUrgencyState(task, day);
        PreferenceFitState preferenceFitState = computePreferenceFitState(task, day.getDayOfWeek());
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

    private MultiDayStateSnapshot computeMultiDaySnapshot(Task task, TaskPlanningState state, LocalDate day) {
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
     *       TaskScorer's configured preferred start deviation hours.</li>
     *   <li><b>Urgency</b> — {@code 1 + requiredDays / remainingDays}; overdue tasks use a fixed high value.</li>
     *   <li><b>Aging</b> — snapshot aging force, pre-computed in maintenance, capped at {@link #maxAgingMultiplier}.</li>
     * </ol>
     */
    int score(Task task, LocalDateTime start, LocalDateTime end, String previousTaskId) {
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
        totalPrio = applyUrgencyMultiplier(totalPrio, context.task, context.snapshot.urgencyState());
        totalPrio = applyFollowUpBoost(totalPrio, context, previousTaskId);
        return applyAgingAndSpreadModifiers(totalPrio, context);
    }

    private boolean passesHardConstraintGate(ScoringContext context) {
        if (isAlreadyCompleteForCurrentCycle(context)) return false;
        if (isBudgetInsufficient(context)) return false;
        if (hasReachedDailyRepetitionLimit(context)) return false;
        if (isWithinCooldownWindow(context)) return false;
        if (violatesMinimumInterDaySpacing(context)) return false;
        if (hasReachedPeriodQuota(context)) return false;
        if (isBlockedByIncompletePriorPeriod(context)) return false;
        if (isBelowMinimumSlotDuration(context)) return false;
        if (isBelowRequiredProgressDuration(context)) return false;
        return !isPastClosableDeadline(context);
    }

    private boolean isAlreadyCompleteForCurrentCycle(ScoringContext context) {
        return context.snapshot.completionState().isComplete();
    }


    private boolean isBudgetInsufficient(ScoringContext context) {
        if (budgetEligibilityService == null) {
            return false;
        }
        TaskBudgetEligibilityService.BudgetEligibility eligibility = budgetEligibilityService.eligibilityFor(context.task);
        return !eligibility.enoughBudget();
    }

    private boolean hasReachedDailyRepetitionLimit(ScoringContext context) {
        return context.snapshot.completionState().scheduledToday() >= context.snapshot.repsPerDay();
    }

    private boolean isWithinCooldownWindow(ScoringContext context) {
        return context.snapshot.sinceLast() < context.task.core.cooldown;
    }

    private boolean violatesMinimumInterDaySpacing(ScoringContext context) {
        MultiDayStateSnapshot multiDay = context.snapshot.multiDayStateSnapshot();
        return multiDay.minDayDistance() > 0
                && multiDay.minDayDistance() < multiDay.expectedDayGap() * 0.5;
    }

    private boolean hasReachedPeriodQuota(ScoringContext context) {
        MultiDayStateSnapshot multiDay = context.snapshot.multiDayStateSnapshot();
        return multiDay.totalScheduledReps() >= multiDay.totalRepsInPeriod();
    }

    private boolean isBlockedByIncompletePriorPeriod(ScoringContext context) {
        TaskCore.Repetition rep = context.task.core.repetition;
        return rep != null && rep.completeFirst && rep.carryoverDebt > 0;
    }

    private boolean isBelowMinimumSlotDuration(ScoringContext context) {
        return context.availableTime < context.task.core.minDuration;
    }

    private boolean isBelowRequiredProgressDuration(ScoringContext context) {
        return context.task.core.progress != null
                && context.availableTime < context.task.core.progress.requiredTimePerRep();
    }

    private boolean isPastClosableDeadline(ScoringContext context) {
        return context.snapshot.urgencyState().isDeadlineExpired();
    }

    private int applyBasePriorityAndChildInfluence(ScoringContext context) {
        return Math.max(context.task.core.priority.value, context.snapshot.maxChildPriority());
    }

    private UrgencyState computeUrgencyState(Task task, LocalDate day) {
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

    private int applyUrgencyMultiplier(int score, Task task, UrgencyState urgencyState) {
        double urgency;
        if (urgencyState.remainingDays() <= 0) {
            urgency = 100;
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + urgencyState.requiredDays() / urgencyState.remainingDays();
        } else {
            urgency = 1.0;
        }
        return (int) (score * urgency);
    }

    private PreferenceFitState computePreferenceFitState(Task task, DayOfWeek dayOfWeek) {
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

    private int applyPreferredTimeFit(int baseScore, ScoringContext context) {
        Set<String> consumed = context.snapshot.preferenceFitState().consumedPrefSlotIds();
        PrefSlotMatch match = findClosestUnconsumedPrefSlot(
                context.snapshot.preferenceFitState().todayPrefSlots(),
                context.start.toLocalTime(),
                consumed
        );
        if (match == null) {
            return context.snapshot.preferenceFitState().hasDayConstraints() ? 0 : baseScore;
        }

        double dif = Duration.between(context.start.toLocalTime(), match.start).toMinutes() / 60.0;
        double fit = Math.max(0, 1 - Math.abs(dif / preferredStartDeviationHours));
        return (int) (baseScore * fit);
    }

    static final class PrefSlotMatch {
        final TaskPrefSlot prefSlot;
        final LocalTime start;

        PrefSlotMatch(TaskPrefSlot prefSlot, LocalTime start) {
            this.prefSlot = prefSlot;
            this.start = start;
        }
    }

    private PrefSlotMatch findClosestUnconsumedPrefSlot(List<TaskPrefSlot> preferredSlots,
                                                         LocalTime candidateStart,
                                                         Set<String> consumedIds) {
        TaskPrefSlot bestSlot = null;
        long minDiff = Long.MAX_VALUE;
        for (TaskPrefSlot slot : preferredSlots) {
            if (consumedIds.contains(slot.id)) continue;
            long slotDiff = Math.abs(Duration.between(candidateStart, slot.start).toMinutes());
            if (slotDiff < minDiff) {
                minDiff = slotDiff;
                bestSlot = slot;
            }
        }
        return bestSlot != null ? new PrefSlotMatch(bestSlot, bestSlot.start) : null;
    }

    private int applyFollowUpBoost(int score, ScoringContext context, String previousTaskId) {
        if (previousTaskId == null || previousTaskId.equals(context.task.core.id)) {
            return score;
        }

        TaskTransitionStat stat = null;
        Map<String, TaskTransitionStat> fromMap = transitionStats.get(previousTaskId);
        if (fromMap != null) {
            stat = fromMap.get(context.task.core.id);
        }
        if (stat == null || stat.weight <= 0) {
            logFollowBoost(context, previousTaskId, 0, 1.0, 0.0, score, score);
            return score;
        }

        double multBoost = Math.min(FOLLOW_UP_MULTIPLIER_CAP, stat.weight * FOLLOW_UP_MULTIPLIER_PER_WEIGHT);
        double addBoost = Math.min(FOLLOW_UP_ADDITIVE_CAP, stat.weight * FOLLOW_UP_ADDITIVE_PER_WEIGHT);
        int boosted = (int) Math.round(score * (1.0 + multBoost) + addBoost);
        logFollowBoost(context, previousTaskId, stat.weight, 1.0 + multBoost, addBoost, score, boosted);
        return boosted;
    }

    private void logFollowBoost(ScoringContext context,
                                String previousTaskId,
                                int weight,
                                double multiplier,
                                double additive,
                                int base,
                                int result) {
        if (logger == null) {
            return;
        }
        logger.accept("follow-boost prev=" + previousTaskId
                + " -> task=" + context.task.core.title
                + "(" + context.task.core.id + ")"
                + " weight=" + weight
                + " mult=" + String.format(java.util.Locale.US, "%.2f", multiplier)
                + " add=" + (int) Math.round(additive)
                + " base=" + base
                + " result=" + result);
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

    void onSlotAssigned(Task task, LocalTime assignedStart) {
        TaskScoringSnapshot snapshot = caches.get(task.core.id);
        if (snapshot == null) return;

        PrefSlotMatch match = findClosestUnconsumedPrefSlot(
                snapshot.preferenceFitState().todayPrefSlots(),
                assignedStart,
                snapshot.preferenceFitState().consumedPrefSlotIds()
        );

        if (match != null) {
            caches.put(task.core.id, snapshot.withAssignedPrefSlot(match.prefSlot.id));
        } else {
            caches.put(task.core.id, snapshot.withIncrementedScheduledToday());
        }
    }

    boolean isPrefSlotConsumed(String taskId, String prefSlotId) {
        TaskScoringSnapshot snapshot = caches.get(taskId);
        return snapshot != null
                && snapshot.preferenceFitState().consumedPrefSlotIds().contains(prefSlotId);
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

