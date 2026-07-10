package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TransitionStat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Scores tasks for scheduling priority using a multi-layer multiplicative formula.
 * <p>
 * Holds a per-task {@link TaskScoringSnapshot} keyed by task ID. Intended lifecycle per generation run:
 * {@link #reset()} once, then {@link #maintenance(Task, LocalDate, TaskPlanningState)} for each task to pre-compute caches,
 * then {@link #score(Task, LocalDateTime, LocalDateTime, String)} for each candidate placement.
 * After a slot is assigned, call {@link #onSlotAssigned(Task, LocalTime)} to update the daily counter.
 */
final class TaskScorer {

    /** Upper bound for the aging multiplier — score boost caps at 3x no matter how long since last activity. */
    private static final double DEFAULT_MAX_AGING_MULTIPLIER = 3.0;
    /** Hours of deviation from preferred start at which the fit factor decays to 0.0. */
    private static final double DEFAULT_PREFERRED_START_DEVIATION_HOURS = 8.0;
    /** Days between completions at which the aging multiplier increases by 1.0. */
    private static final int AGING_SCALE_DAYS = 10;
    /**
     * Follow-up boost constants — applied in {@link #applyFollowUpBoost} when the task
     * being scored frequently followed the previously placed task in historical data.
     *
     * <p>Final score = {@code base × (1 + multBoost) + addBoost}, where:
     * <ul>
     *   <li>{@code multBoost = min(FOLLOW_UP_MULTIPLIER_CAP, weight × FOLLOW_UP_MULTIPLIER_PER_WEIGHT)}</li>
     *   <li>{@code addBoost  = min(FOLLOW_UP_ADDITIVE_CAP,  weight × FOLLOW_UP_ADDITIVE_PER_WEIGHT)}</li>
     * </ul>
     * The additive term ensures even low-priority tasks can follow a high-weight transition.
     * The multiplicative term scales with the task's own score, so high-priority tasks get a
     * proportionally larger boost.
     */
    private static final double FOLLOW_UP_MULTIPLIER_PER_WEIGHT = 0.08;
    private static final double FOLLOW_UP_ADDITIVE_PER_WEIGHT = 120.0;
    private static final double FOLLOW_UP_MULTIPLIER_CAP = 1.6;
    private static final double FOLLOW_UP_ADDITIVE_CAP = 1800.0;
    /** Urgency multiplier applied when a task's deadline has already passed. */
    private static final double OVERDUE_URGENCY_FACTOR = 100.0;
    /** Minimum spread factor — a task with zero day distance still receives this fraction of its score. */
    private static final double SPREAD_FLOOR = 0.1;
    /** Complementary spread range — fraction of score that scales linearly with day-distance ratio. */
    private static final double SPREAD_RANGE = 0.9;
    /**
     * Fraction of the expected inter-day gap below which a task is considered too recently
     * scheduled on another day and must be skipped (inter-day spacing guard).
     */
    private static final double INTER_DAY_SPACING_THRESHOLD = 0.5;
    /**
     * Assumed scheduling window width in days. Used in {@link #computeMultiDaySnapshot} to
     * calculate how many repetition periods fit in a week and derive the total rep quota.
     * This is a fixed design assumption — the window is always treated as one week.
     */
    private static final int SCHEDULING_WINDOW_DAYS = 7;

    private final double maxAgingMultiplier;
    private final double preferredStartDeviationHours;
    private final Map<String, TaskScoringSnapshot> caches = new HashMap<>();
    private final Map<String, Map<String, TransitionStat>> transitionStats = new HashMap<>();
    private final Map<String, LocalDate> lastMaintenanceDays = new HashMap<>();
    private final Consumer<String> logger;
    private final TaskBudgetEligibilityService budgetEligibilityService;

    static final class Builder {
        private Consumer<String> logger;
        private TaskBudgetEligibilityService budgetEligibilityService;
        private double maxAgingMultiplier = DEFAULT_MAX_AGING_MULTIPLIER;
        private double preferredStartDeviationHours = DEFAULT_PREFERRED_START_DEVIATION_HOURS;

        Builder logger(Consumer<String> logger) {
            this.logger = logger;
            return this;
        }

        Builder budgetEligibilityService(TaskBudgetEligibilityService budgetEligibilityService) {
            this.budgetEligibilityService = budgetEligibilityService;
            return this;
        }

        Builder maxAgingMultiplier(double maxAgingMultiplier) {
            this.maxAgingMultiplier = maxAgingMultiplier;
            return this;
        }

        Builder preferredStartDeviationHours(double preferredStartDeviationHours) {
            this.preferredStartDeviationHours = preferredStartDeviationHours;
            return this;
        }

        TaskScorer build() {
            return new TaskScorer(logger, budgetEligibilityService, maxAgingMultiplier, preferredStartDeviationHours);
        }
    }

    private TaskScorer(Consumer<String> logger,
                       TaskBudgetEligibilityService budgetEligibilityService,
                       double maxAgingMultiplier,
                       double preferredStartDeviationHours) {
        this.logger = logger;
        this.budgetEligibilityService = budgetEligibilityService;
        this.maxAgingMultiplier = maxAgingMultiplier;
        this.preferredStartDeviationHours = preferredStartDeviationHours;
    }

    void reset() {
        caches.clear();
        lastMaintenanceDays.clear();
    }

    /**
     * Loads historical task-transition statistics used by the follow-up boost in
     * {@link #score}. A "transition" is an observed pattern where task B frequently
     * followed task A in completed schedules. Tasks that commonly follow the previously
     * placed task receive an additive + multiplicative score boost, encouraging the
     * scheduler to group contextually related tasks.
     *
     * <p>Call once per generation run, after {@link #reset()}, before {@link #score}.
     * Passing an empty list disables the follow-up boost entirely.
     */
    void setTransitionStats(List<TransitionStat> stats) {
        transitionStats.clear();
        for (TransitionStat stat : stats) {
            transitionStats.computeIfAbsent(stat.fromTaskId(), key -> new HashMap<>())
                    .put(stat.toTaskId(), stat);
        }
    }

    /**
     * Snapshot of a task's completion history as of the scheduling day.
     *
     * @param completions       total all-time completions
     * @param lastCompletion    date of the most recent completion; defaults to (created − 1 day)
     *                          when there are no completions, so aging calculations never throw NPE
     * @param isComplete        true if the task has met its quota for this cycle
     *                          (period-based: {@code task.core.repetition.periodCompletions ≥ reps};
     *                          one-off: any completion)
     * @param scheduledToday    number of slots already scheduled for today (including pre-existing ones)
     */
    record CompletionState(int completions,
                           LocalDate lastCompletion,
                           boolean isComplete,
                           int scheduledToday) {
        CompletionState incrementScheduledToday() {
            return new CompletionState(completions, lastCompletion, isComplete, scheduledToday + 1);
        }
    }

    /**
     * Urgency snapshot computed once per task per run.
     *
     * @param remainingDays   days until the deadline or period end (may be negative when overdue)
     * @param requiredDays    days the task is estimated to need based on its duration/repetitions
     * @param isDeadlineExpired true only when {@code closeOnMiss == true} AND the hard deadline
     *                         has passed (day.isAfter(deadline)); blocks scheduling entirely
     */
    record UrgencyState(double remainingDays,
                        double requiredDays,
                        boolean isDeadlineExpired) {
    }

    record EffectiveRepetitionState(LocalDate periodStart,
                                    int periodCompletions,
                                    int carryoverDebt,
                                    int periodInDays) {
        LocalDate periodEnd() {
            return periodStart != null ? periodStart.plusDays(periodInDays) : null;
        }
    }

    /**
     * Snapshot of which preferred time slots apply to today and which have already been
     * matched (consumed) by a previously assigned slot.
     *
     * <p><b>Consumed-set semantics:</b> each {@link TaskPrefSlot} is treated as a one-time
     * "token" per scheduling day. When a task is placed at a time that matches a pref slot,
     * that pref slot is added to {@code consumedPrefSlotIds} so the next placement of the same
     * task on the same day picks the next-closest unconsumed pref slot instead of reusing the
     * same one. This implements a one-to-one assignment between pref slots and scheduled slots.
     */
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

    /**
     * Cross-day scheduling snapshot computed from {@link TaskPlanningState}.
     *
     * @param totalScheduledReps  total reps already placed for this task across the planning window
     * @param totalRepsInPeriod   target reps for the window (reps × number of periods that fit in 7 days);
     *                            used as the quota ceiling to prevent over-scheduling
     * @param minDayDistance      smallest number of days between the scheduling day and any day on
     *                            which this task is already placed; {@code Integer.MAX_VALUE} if not
     *                            placed anywhere else yet
     * @param expectedDayGap      ideal number of days between consecutive placements
     *                            ({@code periodInDays / reps}), used for the inter-day spacing guard
     *                            and the spread modifier
     */
    record MultiDayStateSnapshot(int totalScheduledReps,
                                 int totalRepsInPeriod,
                                 int minDayDistance,
                                 double expectedDayGap) {
    }

    /**
     * Immutable per-task cache produced by {@link #maintenance} and updated incrementally
     * by {@link #onSlotAssigned}.  Holds everything {@link #score} needs to evaluate a
     * candidate time slot without re-reading or re-computing domain state.
     *
     * @param sinceLast        days elapsed since last completion (used in aging formula)
     * @param agingForce       pre-computed aging multiplier: {@code min(1 + sinceLast/AGING_SCALE_DAYS, maxAgingMultiplier)}
     * @param repsPerDay       maximum slots that may be scheduled for this task today
     *                         ({@code TaskCore.Repetition.repsPerDay()})
     * @param maxChildPriority highest {@code scoringWeight} among all descendants; 0 if no children.
     *                         A parent inherits this when it exceeds its own priority weight,
     *                         ensuring parents are pulled up by high-priority children.
     */
    record TaskScoringSnapshot(CompletionState completionState,
                               UrgencyState urgencyState,
                               PreferenceFitState preferenceFitState,
                               MultiDayStateSnapshot multiDayStateSnapshot,
                               int sinceLast,
                               double agingForce,
                               int repsPerDay,
                               int maxChildPriority,
                               boolean budgetEligible) {
        TaskScoringSnapshot withIncrementedScheduledToday() {
            return new TaskScoringSnapshot(
                    completionState.incrementScheduledToday(),
                    urgencyState,
                    preferenceFitState,
                    multiDayStateSnapshot,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority,
                    budgetEligible
            );
        }

        // Assigning a slot always schedules one repetition for today; when it matches a preference slot,
        // we also mark that preference slot as consumed to avoid reusing it.
        TaskScoringSnapshot withAssignedPrefSlot(String prefSlotId) {
            return new TaskScoringSnapshot(
                    completionState.incrementScheduledToday(),
                    urgencyState,
                    preferenceFitState.withConsumedPrefSlot(prefSlotId),
                    multiDayStateSnapshot,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority,
                    budgetEligible
            );
        }

        TaskScoringSnapshot withMultiDaySnapshot(MultiDayStateSnapshot freshMultiDay) {
            return new TaskScoringSnapshot(
                    completionState,
                    urgencyState,
                    preferenceFitState,
                    freshMultiDay,
                    sinceLast,
                    agingForce,
                    repsPerDay,
                    maxChildPriority,
                    budgetEligible
            );
        }
    }

    /**
     * Pre-computes and caches all per-task scoring inputs for {@code day}.
     * Must be called exactly once per task per generation run, before {@link #score}.
     *
     * <p>Side effects (intentional):
     * <ul>
     *   <li>{@link TaskLifecycleManager#advancePeriods} — may reset {@code periodCompletions}
     *       to 0 and advance the period window if the period has turned over.</li>
     * </ul>
     */
    void maintenance(Task task, LocalDate day, TaskPlanningState state) {
        // If already computed for this task+day, only refresh the planning-state-dependent
        // multi-day snapshot (which changes after each placement). Skip the expensive
        // scanSlots, advancePeriods, computeMaxChildPriority, and budget eligibility checks,
        // which produce identical results for repeated calls on the same task+day pair.
        LocalDate lastDay = lastMaintenanceDays.get(task.core.id);
        TaskScoringSnapshot existing = caches.get(task.core.id);
        if (day.equals(lastDay) && existing != null) {
            caches.put(task.core.id, existing.withMultiDaySnapshot(computeMultiDaySnapshot(task, state, day)));
            return;
        }

        lastMaintenanceDays.put(task.core.id, day);
        EffectiveRepetitionState repetitionState = computeEffectiveRepetitionState(task, day);
        CompletionState completionState = scanSlots(task, day, repetitionState);
        UrgencyState urgencyState = computeUrgencyState(task, day, repetitionState);
        PreferenceFitState preferenceFitState = computePreferenceFitState(task, day.getDayOfWeek());
        MultiDayStateSnapshot multiDayStateSnapshot = computeMultiDaySnapshot(task, state, day);

        int sinceLast = (int) ChronoUnit.DAYS.between(completionState.lastCompletion(), day);
        double agingForce = Math.min(1 + ((double) sinceLast / AGING_SCALE_DAYS), maxAgingMultiplier);
        int maxChildPriority = computeMaxChildPriority(task);
        boolean budgetEligible = budgetEligibilityService == null
                || budgetEligibilityService.eligibilityFor(task);

        TaskScoringSnapshot snapshot = new TaskScoringSnapshot(
                completionState,
                urgencyState,
                preferenceFitState,
                multiDayStateSnapshot,
                sinceLast,
                agingForce,
                task.core.repetition.repsPerDay(),
                maxChildPriority,
                budgetEligible
        );
        caches.put(task.core.id, snapshot);
    }

    /**
     * Derives {@link CompletionState} by iterating the task's slot list once.
     *
     * <p>Computes:
     * <ul>
     *   <li>{@code completions} — total all-time completed slots.</li>
     *   <li>{@code lastCompletion} — most recent completion date; defaults to
     *       {@code task.core.created − 1 day} when there are no completions, so
     *       aging calculations always have a valid reference point.</li>
     *   <li>{@code scheduledToday} — slots already present in the task list for {@code today}
     *       (including completed and in-progress ones), used to enforce {@code repsPerDay}.</li>
     *   <li>{@code isComplete} — true if the quota is met: for periodic tasks,
     *       {@code task.core.repetition.periodCompletions ≥ rep.reps}; for one-off tasks, any completion.</li>
     * </ul>
     */
    private CompletionState scanSlots(Task task, LocalDate today, EffectiveRepetitionState repetitionState) {
        int completions = 0;
        int scheduledToday = 0;
        LocalDate lastCompletion = task.core.created.minusDays(1);

        TaskCore.Repetition rep = task.core.repetition;
        for (TaskSlot slot : task.slots) {
            if (slot.day == null) {
                continue;
            }
            if (slot.completed) {
                completions++;
                if (slot.day.isAfter(lastCompletion)) {
                    lastCompletion = slot.day;
                }
            }
            if (slot.day.equals(today) && (slot.scheduled || slot.completed || slot.realStart != null)) {
                scheduledToday++;
            }
        }

        boolean isComplete = (rep != null && rep.reps > 0 && rep.periodUnit != null && repetitionState != null)
                ? repetitionState.periodCompletions() >= rep.reps
                : completions > 0;
        return new CompletionState(completions, lastCompletion, isComplete, scheduledToday);
    }

    /**
     * Recursively walks {@code task.children} to find the highest {@code scoringWeight}
     * among all descendants. Returns 0 if the task has no children.
     *
     * <p>Used in {@link #applyBasePriorityAndChildInfluence}: when a child's priority exceeds
     * the parent's own weight, the parent is scheduled as if it had the child's priority.
     * This ensures that a low-priority parent blocking a high-priority child does not
     * indefinitely suppress the child.
     */
    private int computeMaxChildPriority(Task task) {
        int maxChildPriority = 0;
        for (Task child : task.children) {
            int childMax = Math.max(child.core.priority.scoringWeight, computeMaxChildPriority(child));
            maxChildPriority = Math.max(maxChildPriority, childMax);
        }
        return maxChildPriority;
    }

    /**
     * Computes {@link MultiDayStateSnapshot} for {@code task} on {@code day}.
     *
     * <p>The "window" is always assumed to be 7 days (one week). The number of periods
     * that fit in 7 days is {@code ceil(7 / periodInDays)}; the total expected reps
     * for the week is that count × reps-per-period.  This sets the quota ceiling used
     * by {@link #hasReachedPeriodQuota} to prevent over-scheduling during window generation.
     *
     * <p>For tasks with no repetition config the expected gap defaults to 7 days
     * (one-off tasks should not appear more than once a week).
     */
    private MultiDayStateSnapshot computeMultiDaySnapshot(Task task, TaskPlanningState state, LocalDate day) {
        TaskCore.Repetition rep = task.core.repetition;
        int totalScheduledReps = state.getTotalScheduledReps(task.core.id);
        int minDayDistance = state.minDayDistance(task.core.id, day);
        int totalRepsInPeriod;
        double expectedDayGap;

        if (rep != null && rep.reps > 0) {
            int periodsInWindow = Math.max(1, (int) Math.ceil((double) SCHEDULING_WINDOW_DAYS / rep.periodInDays()));
            totalRepsInPeriod = rep.reps * periodsInWindow;
            expectedDayGap = (double) rep.periodInDays() / rep.reps;
        } else {
            totalRepsInPeriod = 1;
            expectedDayGap = SCHEDULING_WINDOW_DAYS;
        }

        return new MultiDayStateSnapshot(totalScheduledReps, totalRepsInPeriod, minDayDistance, expectedDayGap);
    }

    /**
     * Scores a task for a candidate time slot. Returns 0 if the task cannot be scheduled.
     * <p>
     * {@link #maintenance(Task, LocalDate, TaskPlanningState)} must be called for this task
     * before invoking {@code score()}; an {@link IllegalStateException} is thrown otherwise.
     * <p>
     * Layers applied in order, each multiplying the running total:
     * <ol>
     *   <li><b>Hard constraints</b> — returns 0 if cooldown unmet, slot too short, progress needs
     *       more time, deadline expired with closeOnMiss, already complete, or daily reps exhausted.</li>
     *   <li><b>Priority base</b> — {@code task.core.priority.scoringWeight} (LOW=100 .. CRITICAL=10000).</li>
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
            throw new IllegalStateException(
                    "maintenance() must be called before score() for task: " + task.core.id);
        }

        ScoringContext context = new ScoringContext(task, snapshot, start, (int) ChronoUnit.MINUTES.between(start, end));
        if (!passesHardConstraintGate(context)) {
            return 0;
        }

        int totalPrio = applyBasePriorityAndChildInfluence(context);
        totalPrio = applyPreferredTimeFit(totalPrio, context);
        if (totalPrio <= 0) {
            return 0;
        }
        totalPrio = applyUrgencyMultiplier(totalPrio, context.task(), context.snapshot().urgencyState());
        totalPrio = applyFollowUpBoost(totalPrio, context, previousTaskId);
        return applyAgingAndSpreadModifiers(totalPrio, context);
    }

    private boolean passesHardConstraintGate(ScoringContext context) {
        if (context.snapshot().completionState().isComplete()) return false;
        if (!context.snapshot().budgetEligible()) return false;

        TaskCore core = context.task().core;
        if (core.schedulingType == TaskCore.SchedulingType.TASK
                && core.startDate != null
                && context.start().toLocalDate().isBefore(core.startDate)) {
            return false;
        }

        if (context.snapshot().completionState().scheduledToday() >= context.snapshot().repsPerDay()) return false;
        if (context.snapshot().sinceLast() < context.task().core.cooldown) return false;

        MultiDayStateSnapshot multiDay = context.snapshot().multiDayStateSnapshot();
        if (multiDay.minDayDistance() > 0
                && multiDay.minDayDistance() < multiDay.expectedDayGap() * INTER_DAY_SPACING_THRESHOLD) {
            return false;
        }
        if (multiDay.totalScheduledReps() >= multiDay.totalRepsInPeriod()) return false;

        EffectiveRepetitionState repetitionState = computeEffectiveRepetitionState(
                context.task(), context.start().toLocalDate());
        if (core.repetition != null
                && core.repetition.completeFirst
                && repetitionState != null
                && repetitionState.carryoverDebt() > 0) {
            return false;
        }

        if (context.availableMinutes() < context.task().core.minDuration) return false;
        if (context.task().core.progress != null
                && context.task().core.progress.target > 0
                && context.availableMinutes() < context.task().core.progress.requiredTimePerRep()) {
            return false;
        }
        if (context.snapshot().urgencyState().isDeadlineExpired()) return false;
        return true;
    }

    private int applyBasePriorityAndChildInfluence(ScoringContext context) {
        return Math.max(context.task().core.priority.scoringWeight, context.snapshot().maxChildPriority());
    }

    private UrgencyState computeUrgencyState(Task task, LocalDate day, EffectiveRepetitionState repetitionState) {
        TaskCore.Repetition rep = task.core.repetition;
        double remainingDays;
        if (task.core.deadline != null) {
            remainingDays = (double) ChronoUnit.DAYS.between(day, task.core.deadline);
        } else if (rep != null && rep.reps > 0 && rep.periodUnit != null) {
            LocalDate periodEnd = repetitionState != null ? repetitionState.periodEnd() : rep.periodEnd();
            remainingDays = periodEnd != null
                    ? (double) ChronoUnit.DAYS.between(day, periodEnd)
                    : rep.periodInDays();
        } else {
            remainingDays = 1;
        }

        double requiredDays = task.requiredDays();
        // isAfter is exclusive: deadline day itself is NOT expired (can still schedule),
        // but remainingDays == 0 so applyUrgencyMultiplier will apply OVERDUE_URGENCY_FACTOR that day.
        boolean deadlineExpired = task.core.closeOnMiss && task.core.deadline != null && day.isAfter(task.core.deadline);
        return new UrgencyState(remainingDays, requiredDays, deadlineExpired);
    }

    private EffectiveRepetitionState computeEffectiveRepetitionState(Task task, LocalDate referenceDay) {
        TaskCore.Repetition rep = task.core.repetition;
        if (rep == null || rep.reps <= 0 || rep.periodUnit == null) {
            return null;
        }

        LocalDate periodStart = rep.periodStart != null ? rep.periodStart : task.core.created;
        int periodDays = rep.periodInDays();
        if (periodStart == null || periodDays <= 0) {
            return null;
        }

        int periodCompletions = rep.periodCompletions;
        int carryoverDebt = rep.carryoverDebt;
        if (referenceDay.isBefore(periodStart.plusDays(periodDays))) {
            return new EffectiveRepetitionState(periodStart, periodCompletions, carryoverDebt, periodDays);
        }

        int missingReps = Math.max(0, rep.reps - periodCompletions);
        long daysSinceStart = ChronoUnit.DAYS.between(periodStart, referenceDay);
        long fullPeriods = daysSinceStart / periodDays;
        if (rep.completeFirst) {
            carryoverDebt += missingReps;
            if (fullPeriods > 1) {
                carryoverDebt += (int) ((fullPeriods - 1) * rep.reps);
            }
        } else {
            carryoverDebt = 0;
        }

        return new EffectiveRepetitionState(
                periodStart.plusDays(fullPeriods * periodDays),
                0,
                Math.max(0, carryoverDebt),
                periodDays
        );
    }

    private int applyUrgencyMultiplier(int score, Task task, UrgencyState urgencyState) {
        double urgency;
        if (urgencyState.remainingDays() <= 0) {
            urgency = OVERDUE_URGENCY_FACTOR;
        } else if (task.core.deadline != null || (task.core.repetition != null && task.core.repetition.reps > 0)) {
            urgency = 1.0 + urgencyState.requiredDays() / urgencyState.remainingDays();
        } else {
            urgency = 1.0;
        }
        return (int) (score * urgency);
    }

    /**
     * Filters {@code task.prefSlots} down to those that apply today and detects whether
     * any pref slot imposes a day constraint.
     *
     * <p><b>Day-constraint semantics:</b>
     * <ul>
     *   <li>A {@link com.autosecretary.features.task.domain.model.TaskPrefSlot} imposes a day constraint
     *       when its {@code days} set is non-null and non-empty. Such a slot only applies on
     *       the listed days of the week.</li>
     *   <li>If <em>any</em> pref slot has day constraints ({@code hasDayConstraints == true})
     *       but <em>none</em> of those slots match today, {@link #applyPreferredTimeFit} returns
     *       0 — a hard block, not just a penalty. The task is considered invalid for today.</li>
     *   <li>If no pref slot has day constraints ({@code hasDayConstraints == false}), the task
     *       is allowed on any day of the week with no time-fit penalty when no preferred slot
     *       exists.</li>
     * </ul>
     */
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

    /**
     * Applies a linear decay factor based on how far the candidate start deviates from the
     * nearest unconsumed preferred-time slot.
     *
     * <p><b>Hard-constraint behaviour:</b> when the task has day constraints
     * ({@link PreferenceFitState#hasDayConstraints} is true) but none of the preferred slots
     * match today, this returns {@code 0} — effectively a hard block, not just a score penalty.
     * This is intentional: if the user constrained a task to specific days, scheduling it on an
     * off-day is considered invalid. When there are no day constraints at all, the task is
     * allowed on any day with no time-fit penalty if no preferred slot exists.
     *
     * <p>Fit formula: {@code fit = max(0, 1 − |deviationHours / preferredStartDeviationHours|)}.
     * A candidate that exactly matches the preferred start gets {@code fit = 1.0};
     * a candidate outside the configured deviation window gets {@code fit = 0.0} (blocked).
     */
    private int applyPreferredTimeFit(int baseScore, ScoringContext context) {
        PreferenceFitState pref = context.snapshot().preferenceFitState();
        TaskPrefSlot match = findClosestUnconsumedPrefSlot(
                pref.todayPrefSlots(),
                context.start().toLocalTime(),
                pref.consumedPrefSlotIds()
        );
        if (match == null) {
            return pref.hasDayConstraints() ? 0 : baseScore;
        }

        double deviationHours = Duration.between(context.start().toLocalTime(), match.start).toMinutes() / 60.0;
        double fit = Math.max(0, 1 - Math.abs(deviationHours / preferredStartDeviationHours));
        return (int) (baseScore * fit);
    }

    private TaskPrefSlot findClosestUnconsumedPrefSlot(List<TaskPrefSlot> preferredSlots,
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
        return bestSlot;
    }

    private int applyFollowUpBoost(int score, ScoringContext context, String previousTaskId) {
        if (previousTaskId == null || previousTaskId.equals(context.task().core.id)) {
            return score;
        }

        Map<String, TransitionStat> fromMap = transitionStats.get(previousTaskId);
        TransitionStat stat = fromMap != null ? fromMap.get(context.task().core.id) : null;
        if (stat == null || stat.weight() <= 0) {
            return score;
        }

        double multBoost = Math.min(FOLLOW_UP_MULTIPLIER_CAP, stat.weight() * FOLLOW_UP_MULTIPLIER_PER_WEIGHT);
        double addBoost = Math.min(FOLLOW_UP_ADDITIVE_CAP, stat.weight() * FOLLOW_UP_ADDITIVE_PER_WEIGHT);
        int boosted = (int) Math.round(score * (1.0 + multBoost) + addBoost);
        logFollowBoost(context, previousTaskId, stat.weight(), 1.0 + multBoost, addBoost, score, boosted);
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
                + " -> task=" + context.task().core.title
                + "(" + context.task().core.id + ")"
                + " weight=" + weight
                + " mult=" + String.format(Locale.US, "%.2f", multiplier)
                + " add=" + (int) Math.round(additive)
                + " base=" + base
                + " result=" + result);
    }

    /**
     * Applies the aging multiplier and, optionally, a spread penalty.
     *
     * <p><b>Aging:</b> multiplies the score by {@link TaskScoringSnapshot#agingForce()} (pre-computed
     * in {@link #maintenance}). A task not touched for 10 days ({@link #AGING_SCALE_DAYS}) gets 2×,
     * capped at {@link #maxAgingMultiplier}.
     *
     * <p><b>Spread penalty:</b> discourages re-scheduling a task too soon after a prior placement.
     * When {@code minDayDistance > 0} and less than {@code expectedDayGap}, the score is multiplied
     * by {@code SPREAD_FLOOR + (minDayDistance / expectedDayGap) × SPREAD_RANGE}, producing a value
     * in [{@link #SPREAD_FLOOR}, 1.0].  At exactly {@code expectedDayGap} the factor reaches 1.0
     * (no penalty). The floor ensures a task is never suppressed entirely by the spread alone.
     */
    private int applyAgingAndSpreadModifiers(int score, ScoringContext context) {
        TaskScoringSnapshot snapshot = context.snapshot();
        int adjustedScore = (int) (score * snapshot.agingForce());
        MultiDayStateSnapshot multiDay = snapshot.multiDayStateSnapshot();
        int minDist = multiDay.minDayDistance();
        if (minDist > 0 && minDist != Integer.MAX_VALUE && minDist < multiDay.expectedDayGap()) {
            double ratio = minDist / multiDay.expectedDayGap();
            double spread = Math.min(1.0, SPREAD_FLOOR + ratio * SPREAD_RANGE);
            adjustedScore = (int) (adjustedScore * spread);
        }
        return adjustedScore;
    }

    /**
     * Updates the cached snapshot after a slot is placed for {@code task}.
     *
     * <p>Always increments {@code scheduledToday} in the {@link CompletionState}.
     * Additionally, if the assigned start time matches an unconsumed preferred slot,
     * that preferred slot is marked consumed so the next call to {@link #score} will
     * pick the next-closest pref slot instead of reusing the same one.
     *
     * <p>Must be called after every placement, including pre-existing slots discovered
     * at the start of a single-day run.
     */
    void onSlotAssigned(Task task, LocalTime assignedStart) {
        TaskScoringSnapshot snapshot = caches.get(task.core.id);
        if (snapshot == null) return;

        TaskPrefSlot match = findClosestUnconsumedPrefSlot(
                snapshot.preferenceFitState().todayPrefSlots(),
                assignedStart,
                snapshot.preferenceFitState().consumedPrefSlotIds()
        );

        caches.put(task.core.id, match != null
                ? snapshot.withAssignedPrefSlot(match.id)
                : snapshot.withIncrementedScheduledToday());
    }

    /**
     * Immutable argument bundle passed through all private scoring helper methods.
     * Groups the per-evaluation inputs together to avoid repeating four parameters
     * on every private method signature.
     */
    record ScoringContext(Task task, TaskScoringSnapshot snapshot, LocalDateTime start, int availableMinutes) {}
}
