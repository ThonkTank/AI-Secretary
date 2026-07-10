package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import static com.autosecretary.features.task.domain.scheduling.SchedulingConflict.ReasonCode.*;
import com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskTransitionStatLoader;
import com.autosecretary.features.task.domain.TaskTreeOperations;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Internal implementation of {@link TaskSlotGenerator} that assigns tasks to time slots
 * using a competitive, displacement-aware greedy algorithm.
 *
 * <h2>Two operating modes</h2>
 * <ul>
 *   <li><b>Single-day</b> ({@link #generateSlotsForDay}): schedules one calendar day end-to-end.
 *       Calls {@code scorer.maintenance()} for all tasks upfront before entering the placement
 *       loop, which is the safe order because maintenance mutates task state.</li>
 *   <li><b>Multi-day window</b> ({@link #generateSlotsForWindow}): schedules several consecutive
 *       days in one pass, distributing repetitions intelligently across the window.
 *       Fixed-time tasks ({@code TERMIN}) are pinned first; then the global best-fit loop
 *       competes all chains across all days simultaneously.</li>
 * </ul>
 *
 * <h2>Overall algorithm (global best-fit)</h2>
 * <ol>
 *   <li>Build prerequisite chains via {@link #buildTaskChains} — linear sequences of tasks
 *       that must execute in order (A → B → C).</li>
 *   <li>Repeat until no net-positive placement remains (or the safety cap fires):
 *       <ul>
 *         <li>For each chain and each candidate start time, evaluate
 *             {@link #tryPlaceChain}: compute gain score, find any displaceable slots
 *             that would be evicted, compute loss score, net = gain − loss.</li>
 *         <li>Pick the globally highest net-score winner across all days and chains.</li>
 *         <li>Apply that placement: remove displaced slots, insert new slot(s).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Occupied-interval invariant</h2>
 * Every method that reads or writes {@code List<OccupiedInterval>} relies on this rule:
 * a {@code null} {@link OccupiedInterval#candidate} means the interval is a hard external
 * calendar block (cannot be displaced); a non-null candidate means it is a task slot that
 * <em>may</em> be displaced if its {@link DisplacementCandidate#displaceable} flag is true.
 */
final class DefaultTaskSlotGenerator implements TaskSlotGenerator {

    private static final String GROUP_PREFIX_CHAIN = "chain:";
    private static final String GROUP_PREFIX_SLOT = "slot:";
    private static final String GROUP_PREFIX_FIXED = "fixed:";

    // Flat tiebreaker bonus per slot when evaluating prerequisite chains.
    // Favours longer chains being placed as a unit over individual task placement.
    private static final int CHAIN_COMPLETION_BONUS_PER_SLOT = 10;

    /**
     * Score assigned to fixed-time (TERMIN) task slots. Using {@code MAX_VALUE / 2}
     * rather than {@code MAX_VALUE} itself prevents overflow when adding the chain
     * completion bonus or comparing against other scores.
     */
    private static final int FIXED_TASK_SCORE = Integer.MAX_VALUE / 2;

    /**
     * A half-open time interval {@code [start, end)}.
     *
     * <p>Implements {@link Comparable} so that lists of intervals (especially
     * {@code occupied}) can be sorted by start time (then end time as a tiebreaker).
     * Many methods in this class rely on {@code occupied} being sorted; the sort is
     * maintained by calling {@code occupied.sort(Interval::compareTo)} after every mutation.
     */
    private static class Interval implements Comparable<Interval> {
        final LocalDateTime start;
        final LocalDateTime end;

        Interval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public int compareTo(Interval other) {
            int cmp = this.start.compareTo(other.start);
            return cmp != 0 ? cmp : this.end.compareTo(other.end);
        }
    }

    /**
     * A time interval that is currently occupied on the schedule.
     *
     * <p>{@code candidate == null} → hard calendar block (never displaceable).
     * {@code candidate != null} → a previously placed task slot; may be displaced if
     * {@link DisplacementCandidate#displaceable} is {@code true}.
     */
    private static class OccupiedInterval extends Interval {
        final DisplacementCandidate candidate;

        OccupiedInterval(LocalDateTime start, LocalDateTime end, DisplacementCandidate candidate) {
            super(start, end);
            this.candidate = candidate;
        }

        boolean isDisplaceable() {
            return candidate != null && candidate.displaceable;
        }
    }

    /**
     * Metadata attached to a placed task slot, used when evaluating whether
     * the slot can be evicted to make room for a higher-scoring chain.
     */
    private static class DisplacementCandidate {
        final Task task;
        final TaskSlot slot;
        /** True if this slot may be evicted to make room for a higher-scoring chain. */
        final boolean displaceable;
        /**
         * True for fixed-time (TERMIN) slots. They are displaceable only by other fixed tasks,
         * never by normally scored tasks, even if their score would be higher.
         */
        final boolean protectedFromNormalTasks;
        /** The score that would be lost if this candidate is evicted; used in net-score calculation. */
        final int lossScore;
        /**
         * Group key for atomic eviction. When two slots share an {@code atomicGroupId} (e.g. a
         * prerequisite chain placed together), both are evicted or neither is — partial eviction
         * of a chain is not allowed. The group loss score is counted only once per unique group ID.
         */
        final String atomicGroupId;

        DisplacementCandidate(Task task,
                              TaskSlot slot,
                              boolean displaceable,
                              boolean protectedFromNormalTasks,
                              int lossScore,
                              String atomicGroupId) {
            this.task = task;
            this.slot = slot;
            this.displaceable = displaceable;
            this.protectedFromNormalTasks = protectedFromNormalTasks;
            this.lossScore = lossScore;
            this.atomicGroupId = atomicGroupId;
        }
    }

    /**
     * One node in a prerequisite chain — a task together with the minimum gap (in minutes)
     * that must elapse after the preceding task finishes before this one may start.
     * The first node in a chain always has {@code minGapFromPrevious == 0}.
     */
    private record ChainNode(Task task, int minGapFromPrevious) {}

    /**
     * A fully evaluated candidate placement for a prerequisite chain.
     *
     * <p>{@code chain}, {@code starts}, and {@code nodeScores} are parallel lists:
     * {@code starts.get(i)} is the proposed start time and {@code nodeScores.get(i)} is the
     * individual task score for {@code chain.get(i)}.
     *
     * <p>{@code netScore = gainScore − lossScore}.  A placement is only applied when
     * {@code netScore > 0}, meaning the incoming chain is worth more than whatever
     * it would displace.
     *
     * @param chain       nodes in prerequisite order
     * @param starts      parallel list of proposed start times for each node
     * @param nodeScores  parallel list of individual task scores for each node (for slot display)
     * @param toDisplace  slots that must be evicted to make room; may be empty
     * @param gainScore   sum of scores for all incoming slots (plus chain-completion bonus)
     * @param lossScore   sum of loss scores for all evicted slots (atomic groups counted once)
     * @param netScore    {@code gainScore − lossScore}; placement is applied only when positive
     * @param day         calendar day of the first node's start time
     */
    private record ChainPlacement(List<ChainNode> chain,
                                  List<LocalDateTime> starts,
                                  List<Integer> nodeScores,
                                  Set<DisplacementCandidate> toDisplace,
                                  int gainScore,
                                  int lossScore,
                                  int netScore,
                                  LocalDate day) {
        ChainPlacement(List<ChainNode> chain,
                       List<LocalDateTime> starts,
                       List<Integer> nodeScores,
                       Set<DisplacementCandidate> toDisplace,
                       int gainScore,
                       int lossScore,
                       LocalDate day) {
            this(chain, starts, nodeScores, toDisplace, gainScore, lossScore, gainScore - lossScore, day);
        }
    }

    /**
     * Snapshot of one day's scheduling state used during multi-day window scheduling.
     * {@code occupied} is a mutable, sorted list of all intervals already claimed on
     * this day (task slots and calendar blocks); it is updated in-place as placements
     * and displacements are applied.
     */
    private record DaySchedulingContext(LocalDate day,
                                        LocalDateTime windowStart,
                                        LocalDateTime windowEnd,
                                        List<OccupiedInterval> occupied) {}

    /**
     * Return value of {@link #initSchedulingRun}: the task forest (roots only) and
     * the fully flattened list of all tasks (roots + all descendants).
     */
    private static final class SchedulingRunContext {
        final TaskPlanningState planningState;
        final Map<String, Task> allTasksById;
        final List<SchedulingConflict> conflicts = new ArrayList<>();
        int newSlots;

        SchedulingRunContext(TaskPlanningState planningState, Map<String, Task> allTasksById) {
            this.planningState = planningState;
            this.allTasksById = allTasksById;
        }
    }

    private record SchedulingRunInit(List<Task> taskTree, List<Task> allTasks, SchedulingRunContext runContext) {}

    private static final SchedulingWindowProvider DEFAULT_WINDOW = SchedulingWindowProvider.DEFAULT;

    private final Consumer<String> logger;
    private final SchedulingWindowProvider schedulingWindowProvider;
    private final CalendarBlockedIntervalProvider calendarBlockedIntervalProvider;
    private final TaskScorer scorer;
    private final TaskTransitionStatLoader transitionStatLoader;
    private SchedulingRunContext currentRunContext;

    private static final java.time.format.DateTimeFormatter HMM = DateFormatters.TIME_HH_MM;

    DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager,
                             Consumer<String> logger,
                             SchedulingWindowProvider schedulingWindowProvider,
                             CalendarBlockedIntervalProvider calendarBlockedIntervalProvider,
                             TaskTransitionStatLoader transitionStatLoader,
                             TaskBudgetEligibilityService taskBudgetEligibilityService) {
        this.scorer = new TaskScorer.Builder()
                .logger(logger)
                .budgetEligibilityService(taskBudgetEligibilityService)
                .build();
        this.transitionStatLoader = transitionStatLoader;
        this.logger = logger;
        this.schedulingWindowProvider = schedulingWindowProvider != null ? schedulingWindowProvider : DEFAULT_WINDOW;
        this.calendarBlockedIntervalProvider = calendarBlockedIntervalProvider != null
                ? calendarBlockedIntervalProvider
                : CalendarBlockedIntervalProvider.NONE;
    }

    /**
     * Populates {@code state} with slots that are already committed (completed or started) in
     * the given date range, so the window scheduler knows to respect them as prior art.
     * Call this before {@link #generateSlotsForWindow} when re-generating a window that
     * may already contain partially completed work.
     */
    public void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive, LocalDate endExclusive, TaskPlanningState state) {
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.day != null
                        && !slot.day.isBefore(startInclusive)
                        && slot.day.isBefore(endExclusive)
                        && (slot.completed || slot.realStart != null)) {
                    state.recordScheduled(task.core.id, slot.day);
                }
            }
        }
    }

    /**
     * Package-private overload for testing and internal use: accepts explicit window bounds
     * instead of deriving them from {@code schedulingWindowProvider}. Bypasses the
     * {@code SchedulingWindowProvider} entirely — useful in unit tests where the window must
     * be controlled precisely. Delegates to the five-argument variant with an empty calendar list.
     */
    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDateTime windowStart, LocalDateTime windowEnd, TaskPlanningState state) {
        return generateSlotsForDay(tasks, windowStart, windowEnd, state, List.of());
    }

    @Override
    public TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) {
        SchedulingWindowProvider.SchedulingWindow window = schedulingWindowProvider.forDay(day);
        return generateSlotsForDayInternal(tasks, window.start(), window.end(), state, new ArrayList<>());
    }

    /**
     * Schedules {@code days} consecutive calendar days starting at {@code startDay}.
     *
     * <p>Unlike {@link #generateSlotsForDay} (which calls {@code scorer.maintenance()} for all
     * tasks upfront), the window path calls maintenance lazily inside
     * {@link #tryPlaceChain} as each chain is evaluated. See the class-level Javadoc and the
     * pre-existing backlog note on coupling for the implications of this difference.
     *
     * <p>Fixed-time tasks ({@code TERMIN}) are pinned per-day first, then the global best-fit
     * loop runs across all days simultaneously to fill remaining gaps.
     */
    @Override
    public TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay, int days, TaskPlanningState state) {
        if (days <= 0) {
            return new TaskSlotGenerationResult(0, List.of());
        }

        SchedulingRunInit init = initSchedulingRun(tasks, state);
        List<Task> taskTree = init.taskTree();
        List<Task> allTasks = init.allTasks();
        SchedulingRunContext runContext = init.runContext();

        List<DaySchedulingContext> contexts = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = startDay.plusDays(i);
            SchedulingWindowProvider.SchedulingWindow window = schedulingWindowProvider.forDay(day);
            List<OccupiedInterval> occupied = collectOccupiedIntervals(allTasks, day, new ArrayList<>());
            for (CalendarBlockedIntervalProvider.BlockedInterval blocked :
                    calendarBlockedIntervalProvider.readBlockedIntervals(day, window.start(), window.end())) {
                occupied.add(new OccupiedInterval(blocked.start(), blocked.end(), null));
            }
            occupied.sort(Interval::compareTo);
            scheduleFixedTasks(taskTree, window.start(), window.end(), occupied, day, runContext);
            contexts.add(new DaySchedulingContext(day, window.start(), window.end(), occupied));
        }

        assignGlobalBestFitAcrossWindow(taskTree, contexts, runContext);
        appendNoGapConflictsForWindow(allTasks, startDay, days, runContext);
        logWindowSummary(allTasks, contexts, runContext);
        return new TaskSlotGenerationResult(runContext.newSlots, runContext.conflicts);
    }

    /**
     * Package-private overload for testing and internal use: accepts explicit window bounds
     * and a pre-collected list of calendar events. Bypasses both {@code schedulingWindowProvider}
     * and {@code calendarBlockedIntervalProvider} — useful in unit tests or when the calling
     * code has already resolved these externally.
     */
    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    TaskPlanningState state,
                                    List<CalendarBlockedIntervalProvider.BlockedInterval> blockedIntervals) {
        return generateSlotsForDayInternal(tasks, windowStart, windowEnd, state, blockedIntervals);
    }

    private TaskSlotGenerationResult generateSlotsForDayInternal(List<Task> tasks,
                                             LocalDateTime windowStart,
                                             LocalDateTime windowEnd,
                                             TaskPlanningState state,
                                             List<CalendarBlockedIntervalProvider.BlockedInterval> blockedIntervals) {
        LocalDate schedulingDay = windowStart.toLocalDate();
        SchedulingRunInit init = initSchedulingRun(tasks, state);
        List<Task> taskTree = init.taskTree();
        List<Task> allTasks = init.allTasks();
        SchedulingRunContext runContext = init.runContext();

        for (Task t : allTasks) {
            scorer.maintenance(t, schedulingDay, state);
        }

        for (Task t : allTasks) {
            for (TaskSlot slot : t.slots) {
                if (slot.day.equals(schedulingDay) && (slot.completed || slot.realStart != null)) {
                    scorer.onSlotAssigned(t, slot.start);
                }
            }
        }

        List<OccupiedInterval> occupied = collectOccupiedIntervals(allTasks, schedulingDay, blockedIntervals);
        for (CalendarBlockedIntervalProvider.BlockedInterval blocked :
                calendarBlockedIntervalProvider.readBlockedIntervals(schedulingDay, windowStart, windowEnd)) {
            occupied.add(new OccupiedInterval(blocked.start(), blocked.end(), null));
        }
        occupied.sort(Interval::compareTo);

        scheduleFixedTasks(taskTree, windowStart, windowEnd, occupied, windowStart.toLocalDate(), runContext);
        assignGlobalBestFit(taskTree, windowStart, windowEnd, occupied, runContext);

        int totalDaySlots = logDaySummary(allTasks, schedulingDay, true);
        log("Gesamt: " + totalDaySlots + " slots (neu: " + runContext.newSlots + ")");
        appendNoGapConflictsForWindow(allTasks, schedulingDay, 1, runContext);
        return new TaskSlotGenerationResult(runContext.newSlots, runContext.conflicts);
    }

    /**
     * Records into {@code state} every slot that is already marked as scheduled for {@code day}
     * but has not yet been recorded there. Used when iterating multi-day windows so that
     * later days know which tasks were already placed on earlier days and can apply the
     * inter-day spacing guard accordingly.
     *
     * <p>Differs from {@link #recordPreservedSlots}: that method captures <em>committed</em>
     * (completed/started) work from a previous generation run; this method captures
     * <em>newly generated</em> scheduled slots within the current run.
     */
    public void recordScheduledSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) {
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.day.equals(day) && slot.scheduled && !state.getScheduledDays(task.core.id).contains(day)) {
                    state.recordScheduled(task.core.id, day);
                }
            }
        }
    }

    /**
     * Resets per-run state (scorer, conflict list, counters, planning state) and builds the task
     * tree and {@code allTasksById} index from the given flat task list. Called at the start of
     * every public scheduling entry point to ensure a clean, consistent run context.
     */
    private SchedulingRunInit initSchedulingRun(List<Task> tasks, TaskPlanningState state) {
        scorer.reset();
        scorer.setTransitionStats(transitionStatLoader != null ? transitionStatLoader.load() : List.of());
        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);
        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);
        Map<String, Task> allTasksById = new HashMap<>();
        for (Task t : allTasks) {
            allTasksById.put(t.core.id, t);
        }
        currentRunContext = new SchedulingRunContext(state, allTasksById);
        return new SchedulingRunInit(taskTree, allTasks, currentRunContext);
    }

    /**
     * Adapts the single-day path to reuse {@link #assignGlobalBestFitAcrossWindow} without
     * duplicating the competitive placement loop. Wraps the single day's parameters into a
     * one-element {@link DaySchedulingContext} list and delegates entirely.
     */
    private void assignGlobalBestFit(List<Task> tasks,
                                     LocalDateTime windowStart,
                                     LocalDateTime windowEnd,
                                     List<OccupiedInterval> occupied,
                                     SchedulingRunContext runContext) {
        assignGlobalBestFitAcrossWindow(tasks,
                List.of(new DaySchedulingContext(windowStart.toLocalDate(), windowStart, windowEnd, occupied)),
                runContext);
    }

    /** Safety cap for the placement loop — prevents infinite scheduling if a bug prevents convergence. */
    private static final int MAX_PLACEMENT_ITERATIONS = 10_000;

    private void assignGlobalBestFitAcrossWindow(List<Task> tasks,
                                                 List<DaySchedulingContext> contexts,
                                                 SchedulingRunContext runContext) {
        // Chains are built from static prerequisite relationships which never change during the loop.
        List<List<ChainNode>> chains = buildTaskChains(tasks);
        int iterations = 0;
        while (iterations < MAX_PLACEMENT_ITERATIONS) {
            iterations++;
            ChainPlacement best = null;
            DaySchedulingContext bestContext = null;

            for (DaySchedulingContext context : contexts) {
                List<LocalDateTime> startPoints = collectStartPoints(
                        findGaps(context.occupied(), context.windowStart(), context.windowEnd()),
                        context.occupied());
                if (startPoints.isEmpty()) {
                    continue;
                }
                ChainPlacement dayBest = null;
                for (List<ChainNode> chain : chains) {
                    ChainPlacement chainBest = evaluateChainCandidates(
                            chain, startPoints, context.windowEnd(), context.occupied(), runContext);
                    if (chainBest != null && chainBest.netScore > 0 && (dayBest == null || chainBest.netScore > dayBest.netScore)) {
                        dayBest = chainBest;
                    }
                }
                if (dayBest != null && (best == null || dayBest.netScore > best.netScore)) {
                    best = dayBest;
                    bestContext = context;
                }
            }

            if (best == null || bestContext == null) {
                break;
            }

            logGlobalCompetition(best, bestContext);
            applyPlacement(best, bestContext.occupied(), runContext);
        }
        if (iterations >= MAX_PLACEMENT_ITERATIONS) {
            log("[WARN] assignGlobalBestFitAcrossWindow hit safety cap of " + MAX_PLACEMENT_ITERATIONS + " iterations");
        }
    }

    private void logGlobalCompetition(ChainPlacement placement, DaySchedulingContext context) {
        StringBuilder chainSummary = new StringBuilder();
        for (int i = 0; i < placement.chain.size(); i++) {
            if (i > 0) chainSummary.append(" -> ");
            chainSummary.append(placement.chain.get(i).task.core.title)
                    .append("@").append(placement.starts.get(i).format(HMM));
        }

        List<String> displacedParts = new ArrayList<>();
        for (DisplacementCandidate candidate : placement.toDisplace) {
            TaskSlot slot = candidate.slot;
            Task owner = candidate.task != null ? candidate.task : currentRunContext.allTasksById.get(slot.taskId);
            String title = owner != null ? owner.core.title : slot.taskId;
            String s = slot.start != null ? slot.start.format(HMM) : "?";
            String e = slot.end != null ? slot.end.format(HMM) : "?";
            displacedParts.add(title + "[" + slot.day + " " + s + "-" + e + "]");
        }
        String displaced = String.join(", ", displacedParts);

        log("[GLOBAL-COMPETE] day=" + context.day()
                + " winner=" + chainSummary
                + " gain=" + placement.gainScore
                + " loss=" + placement.lossScore
                + " net=" + placement.netScore
                + (!displaced.isEmpty() ? " verdrängt=" + displaced : " verdrängt=keine"));
    }

    private void logWindowSummary(List<Task> allTasks,
                                  List<DaySchedulingContext> contexts,
                                  SchedulingRunContext runContext) {
        for (DaySchedulingContext context : contexts) {
            int totalDaySlots = logDaySummary(allTasks, context.day(), false);
            log("Gesamt: " + totalDaySlots + " slots");
        }
        log("Global neu eingeplant: " + runContext.newSlots + " slots");
    }

    /**
     * Logs "=== Zusammenfassung [day] ===" followed by one line per task with scheduled slots.
     *
     * @param includeUnscheduled when {@code true}, also logs a line for tasks with no slots that day
     * @return total number of scheduled slots on that day across all tasks
     */
    private int logDaySummary(List<Task> allTasks, LocalDate day, boolean includeUnscheduled) {
        log("=== Zusammenfassung " + day + " ===");
        int totalDaySlots = 0;
        for (Task t : allTasks) {
            List<TaskSlot> daySlots = new ArrayList<>();
            for (TaskSlot s : t.slots) {
                if (s.day.equals(day) && s.scheduled) daySlots.add(s);
            }
            if (!daySlots.isEmpty()) {
                totalDaySlots += daySlots.size();
                StringBuilder summary = new StringBuilder();
                for (int j = 0; j < daySlots.size(); j++) {
                    if (j > 0) summary.append(", ");
                    summary.append(formatSlot(daySlots.get(j)));
                }
                log("  " + t.core.title + ": " + daySlots.size() + " slots [" + summary + "]");
            } else if (includeUnscheduled) {
                log("  " + t.core.title + ": unscheduled");
            }
        }
        return totalDaySlots;
    }

    private ChainPlacement evaluateChainCandidates(List<ChainNode> fullChain,
                                                   List<LocalDateTime> startPoints,
                                                   LocalDateTime windowEnd,
                                                   List<OccupiedInterval> occupied,
                                                   SchedulingRunContext runContext) {
        ChainPlacement best = null;
        for (LocalDateTime start : startPoints) {
            for (int len = 1; len <= fullChain.size(); len++) {
                List<ChainNode> fitting = fullChain.subList(0, len);
                ChainPlacement candidate = tryPlaceChain(fitting, start, windowEnd, occupied, runContext);
                if (candidate == null || candidate.netScore <= 0) {
                    continue;
                }
                if (best == null || candidate.netScore > best.netScore) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Tries to place {@code chain} starting at {@code firstStart} and returns a
     * {@link ChainPlacement} if the placement is feasible and net-positive, or {@code null}
     * if it is impossible or would lose more than it gains.
     *
     * <p>For each node in the chain the method:
     * <ol>
     *   <li>Checks prerequisites are met (earlier slot must exist and have ended).</li>
     *   <li>Computes the slot end time; returns {@code null} if it exceeds {@code windowEnd}.</li>
     *   <li>Finds any overlapping occupied intervals; returns {@code null} if any are non-displaceable
     *       (calendar blocks or fixed-task-protected slots).</li>
     *   <li>Expands overlapping slots to their full atomic group (chain siblings must be evicted
     *       together), collects their loss scores.</li>
     *   <li>Calls {@code scorer.maintenance()} then {@code scorer.score()} to get the gain for
     *       this node; returns {@code null} if the score is zero (task ineligible).</li>
     * </ol>
     * The chain-completion bonus ({@link #CHAIN_COMPLETION_BONUS_PER_SLOT}) is added after all
     * nodes are processed, before comparing gain vs. loss.
     *
     * <p><b>Note:</b> {@code scorer.maintenance()} inside this method is a side-effecting call
     * (see pre-existing backlog). For the single-day path this is never reached because
     * maintenance is called upfront; it is only reached in the window path.
     */
    private ChainPlacement tryPlaceChain(List<ChainNode> chain,
                                         LocalDateTime firstStart,
                                         LocalDateTime windowEnd,
                                         List<OccupiedInterval> occupied,
                                         SchedulingRunContext runContext) {
        List<LocalDateTime> starts = new ArrayList<>();
        List<Integer> nodeScores = new ArrayList<>();
        Set<DisplacementCandidate> toDisplace = new HashSet<>();
        int gain = 0;
        boolean incomingContainsFixed = false;

        LocalDateTime cursor = firstStart;
        for (int i = 0; i < chain.size(); i++) {
            ChainNode node = chain.get(i);
            Task task = node.task;
            if (task.core.schedulingType == TaskCore.SchedulingType.TERMIN) {
                incomingContainsFixed = true;
            }
            if (i > 0) {
                cursor = cursor.plusMinutes(node.minGapFromPrevious);
            }

            if (hasUnmetPrerequisites(task, cursor, starts, chain, i)) {
                return null;
            }

            int taskDuration = task.core.plannedDurationMinutes();
            LocalDateTime end = cursor.plusMinutes(taskDuration);
            if (!end.isAfter(cursor) || end.isAfter(windowEnd)) {
                return null;
            }

            Set<OccupiedInterval> overlaps = findOverlappingIntervals(occupied, cursor, end);
            for (OccupiedInterval overlap : overlaps) {
                if (!overlap.isDisplaceable()) {
                    return null;
                }
                if (!incomingContainsFixed && overlap.candidate != null && overlap.candidate.protectedFromNormalTasks) {
                    return null;
                }
            }

            expandToFullChains(overlaps, occupied, toDisplace);
            scorer.maintenance(task, cursor.toLocalDate(), runContext.planningState);
            int score = scorer.score(task, cursor, end, findPreviousTaskIdForContext(cursor, starts, chain, i, occupied));
            if (score <= 0) {
                return null;
            }
            gain += score;
            starts.add(cursor);
            nodeScores.add(score);
            cursor = end;
        }

        gain += chain.size() * CHAIN_COMPLETION_BONUS_PER_SLOT;
        int loss = computeAtomicLoss(toDisplace);
        if (gain - loss <= 0) {
            return null;
        }

        return new ChainPlacement(new ArrayList<>(chain), starts, nodeScores, toDisplace, gain, loss, firstStart.toLocalDate());
    }

    /**
     * Identifies the task that most recently ended just before {@code candidateStart},
     * used by {@link TaskScorer#score} to compute the follow-up boost.
     *
     * <p>Two sources are merged to find the "previous" task:
     * <ol>
     *   <li><b>Earlier nodes in the current chain</b> — nodes already committed to
     *       {@code chainStarts} (indices 0..{@code currentIndex-1}) represent tasks that
     *       would run before the current node. Their end time is computed as
     *       {@code chainStarts[i] + plannedDurationMinutes} so that the comparison is
     *       symmetric with the occupied-interval end-time comparison below.</li>
     *   <li><b>Already-placed occupied intervals</b> — slots in {@code occupied} that ended
     *       at or before {@code candidateStart} represent previously scheduled tasks on the
     *       same day.</li>
     * </ol>
     * The source whose end time is latest (closest to {@code candidateStart}) wins.
     */
    private String findPreviousTaskIdForContext(LocalDateTime candidateStart,
                                                List<LocalDateTime> chainStarts,
                                                List<ChainNode> chain,
                                                int currentIndex,
                                                List<OccupiedInterval> occupied) {
        LocalDateTime latest = null;
        String taskId = null;

        for (int i = 0; i < currentIndex; i++) {
            LocalDateTime chainEnd = chainStarts.get(i).plusMinutes(chain.get(i).task.core.plannedDurationMinutes());
            if (!chainEnd.isAfter(candidateStart) && (latest == null || chainEnd.isAfter(latest))) {
                latest = chainEnd;
                taskId = chain.get(i).task.core.id;
            }
        }

        for (OccupiedInterval interval : occupied) {
            if (interval.candidate == null || interval.end == null || interval.end.isAfter(candidateStart)) {
                continue;
            }
            String occupiedTaskId = interval.candidate.task != null ? interval.candidate.task.core.id : interval.candidate.slot.taskId;
            if (occupiedTaskId == null) {
                continue;
            }
            if (latest == null || interval.end.isAfter(latest)) {
                latest = interval.end;
                taskId = occupiedTaskId;
            }
        }
        return taskId;
    }

    /**
     * Sums the loss scores of the candidates to displace, counting each atomic group at most once.
     *
     * <p>When a chain (A → B → C) was placed as a unit, all three slots share an
     * {@code atomicGroupId}. The first slot stores the full chain's {@code gainScore} as its
     * own {@code lossScore}; subsequent slots store 0 (see {@link #applyPlacement}). Deduplication
     * by group ID therefore produces the correct total without double-counting.
     *
     * <p>Candidates without a group ID (single-slot placements) are counted individually.
     */
    private int computeAtomicLoss(Set<DisplacementCandidate> candidates) {
        int loss = 0;
        Set<String> seenGroups = new HashSet<>();
        for (DisplacementCandidate candidate : candidates) {
            boolean counted = candidate.atomicGroupId == null || seenGroups.add(candidate.atomicGroupId);
            if (counted) {
                loss += Math.max(0, candidate.lossScore);
            }
        }
        return loss;
    }

    private boolean hasUnmetPrerequisites(Task task,
                                          LocalDateTime candidateStart,
                                          List<LocalDateTime> chainStarts,
                                          List<ChainNode> chain,
                                          int currentIndex) {
        if (task.prerequisites == null || task.prerequisites.isEmpty()) {
            return false;
        }

        for (TaskPrerequisite prereq : task.prerequisites) {
            Task prereqTask = currentRunContext.allTasksById.get(prereq.prerequisiteId);
            if (prereqTask == null) {
                continue;
            }

            TaskSlot prereqSlot = findLatestScheduledSlotBefore(prereqTask, candidateStart);
            LocalDateTime prereqEnd = prereqSlot != null && prereqSlot.end != null
                    ? LocalDateTime.of(prereqSlot.day, prereqSlot.end)
                    : null;

            if (prereqEnd == null) {
                for (int i = 0; i < currentIndex; i++) {
                    ChainNode n = chain.get(i);
                    if (n.task.core.id.equals(prereq.prerequisiteId)) {
                        prereqEnd = chainStarts.get(i).plusMinutes(n.task.core.plannedDurationMinutes());
                        break;
                    }
                }
            }

            if (prereqEnd == null) {
                return true;
            }

            LocalDateTime earliestStart = prereqEnd.plusMinutes(Math.max(0, prereq.minGapMinutes));
            if (candidateStart.isBefore(earliestStart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts the prerequisite graph into a list of linear chains, each suitable for
     * evaluation by {@link #tryPlaceChain}.
     *
     * <p>A "chain" is a path through the prerequisite DAG: A → B → C means B requires A,
     * C requires B, and the chain [A, B, C] must be placed in order with minimum gap constraints.
     *
     * <p>Tasks with no incoming prerequisites are chain roots. DFS from each root produces one
     * chain per leaf-to-root path. Tasks with no prerequisite relationships at all each become
     * their own single-node chain.
     *
     * <p><b>Scope:</b> only iterates the top-level {@code tasks} parameter. Child tasks in the
     * tree hierarchy are not traversed here; see README.md for the known design implication.
     */
    private List<List<ChainNode>> buildTaskChains(List<Task> tasks) {
        Map<String, List<ChainNode>> outgoing = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (Task task : tasks) {
            if (task.prerequisites == null) {
                continue;
            }
            for (TaskPrerequisite prerequisite : task.prerequisites) {
                if (!currentRunContext.allTasksById.containsKey(prerequisite.prerequisiteId)) {
                    continue;
                }
                outgoing.computeIfAbsent(prerequisite.prerequisiteId, key -> new ArrayList<>())
                        .add(new ChainNode(task, prerequisite.minGapMinutes));
                hasIncoming.add(task.core.id);
            }
        }

        List<Task> starts = new ArrayList<>();
        for (Task task : tasks) {
            if (!hasIncoming.contains(task.core.id)) {
                starts.add(task);
            }
        }

        List<List<ChainNode>> chains = new ArrayList<>();
        for (Task start : starts) {
            List<ChainNode> path = new ArrayList<>();
            path.add(new ChainNode(start, 0));
            dfsBuildChains(start, outgoing, path, new HashSet<>(), chains);
        }

        if (chains.isEmpty()) {
            for (Task task : tasks) {
                chains.add(List.of(new ChainNode(task, 0)));
            }
        }

        return chains;
    }

    /**
     * Depth-first traversal that extends {@code path} one node at a time along the outgoing
     * prerequisite edges and emits a complete chain whenever a leaf (or a cycle) is reached.
     *
     * <p><b>Cycle detection:</b> {@code seen} tracks the nodes currently on the active path.
     * If a node is already in {@code seen} when revisited, a cycle exists; the current path
     * is emitted as-is and the traversal stops that branch. {@code seen.remove(current.core.id)}
     * at the end of each call is the backtracking step — it un-marks the node when the DFS
     * returns up the call stack, so sibling branches can legitimately visit the same node.
     */
    private void dfsBuildChains(Task current,
                                Map<String, List<ChainNode>> outgoing,
                                List<ChainNode> path,
                                Set<String> seen,
                                List<List<ChainNode>> chains) {
        if (!seen.add(current.core.id)) {
            chains.add(new ArrayList<>(path));
            return;
        }

        List<ChainNode> next = outgoing.get(current.core.id);
        if (next == null || next.isEmpty()) {
            chains.add(new ArrayList<>(path));
            seen.remove(current.core.id);
            return;
        }

        boolean extended = false;
        for (ChainNode node : next) {
            if (seen.contains(node.task.core.id)) {
                continue;
            }
            extended = true;
            path.add(node);
            dfsBuildChains(node.task, outgoing, path, seen, chains);
            path.remove(path.size() - 1);
        }

        if (!extended) {
            chains.add(new ArrayList<>(path));
        }
        seen.remove(current.core.id);
    }

    private void applyPlacement(ChainPlacement placement,
                                List<OccupiedInterval> occupied,
                                SchedulingRunContext runContext) {
        removeDisplacedSlots(placement.toDisplace, occupied, runContext);
        String chainId = placement.chain.size() > 1 ? UUID.randomUUID().toString() : null;

        for (int i = 0; i < placement.chain.size(); i++) {
            Task task = placement.chain.get(i).task;
            LocalDateTime start = placement.starts.get(i);
            TaskSlot slot = createScheduledSlot(task, start, placement.nodeScores.get(i));
            int plannedDuration = task.core.plannedDurationMinutes();
            LocalDateTime end = start.plusMinutes(plannedDuration);
            slot.end = end.toLocalTime();
            slot.chainId = chainId;
            // For chains: the first slot carries the full gainScore as displacementScore so that
            // computeAtomicLoss (which deduplicates by atomicGroupId) counts the full chain value
            // exactly once. Subsequent slots get 0 — they are covered by the first slot's count.
            slot.displacementScore = (i == 0) ? placement.gainScore : 0;
            finalizeAssignment(task, slot);
            if (!task.children.isEmpty()) {
                scheduleChildrenWithinSlot(task.children, slot, start, end);
            }
            occupied.add(new OccupiedInterval(start, end, toCandidate(task, slot, true)));
        }
        occupied.sort(Interval::compareTo);
    }

    /**
     * Permanently removes evicted slots from both the in-memory task model and the occupied-interval list.
     *
     * <p>Two structures are mutated:
     * <ol>
     *   <li>{@code task.slots} — the slot is removed from the owning task's slot list so it will
     *       not be persisted to the database on the next save.</li>
     *   <li>{@code occupied} — the interval entry is removed so subsequent start-point and
     *       gap calculations on the same day see the freed time.</li>
     * </ol>
     * If a slot's task cannot be found in {@link #allTasksById} (e.g. stale ID), the slot is
     * still removed from {@code occupied} to keep the interval list consistent.
     */
    private void removeDisplacedSlots(Set<DisplacementCandidate> displaced,
                                      List<OccupiedInterval> occupied,
                                      SchedulingRunContext runContext) {
        if (displaced.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (DisplacementCandidate candidate : displaced) {
            TaskSlot slot = candidate.slot;
            ids.add(slot.id);
            Task owner = runContext.allTasksById.get(slot.taskId);
            if (owner != null) {
                owner.slots.removeIf(existing -> existing.id.equals(slot.id));
            }
            if (runContext.planningState != null && slot.day != null) {
                runContext.planningState.removeScheduled(slot.taskId, slot.day);
            }
        }
        occupied.removeIf(interval -> interval.candidate != null
                && interval.candidate.slot != null
                && ids.contains(interval.candidate.slot.id));
        removeOrphanedChildSlots(ids, runContext);
    }

    /**
     * Removes child slots whose {@code parent} references a displaced slot.
     * When a parent slot is evicted, its children (scheduled via
     * {@link #scheduleChildrenWithinSlot}) become orphans and must be cleaned up.
     * Recurses to handle grandchildren and deeper nesting.
     *
     * <p>Scans all tasks because child slots belong to different tasks than the
     * parent slot — there is no single task whose slot list contains both.
     */
    private void removeOrphanedChildSlots(Set<String> displacedSlotIds, SchedulingRunContext runContext) {
        Set<String> orphanedIds = new HashSet<>();
        for (Task task : runContext.allTasksById.values()) {
            Iterator<TaskSlot> it = task.slots.iterator();
            while (it.hasNext()) {
                TaskSlot slot = it.next();
                if (slot.parent != null && displacedSlotIds.contains(slot.parent)) {
                    orphanedIds.add(slot.id);
                    if (runContext.planningState != null && slot.day != null) {
                        runContext.planningState.removeScheduled(slot.taskId, slot.day);
                    }
                    runContext.newSlots--;
                    it.remove();
                }
            }
        }
        if (!orphanedIds.isEmpty()) {
            removeOrphanedChildSlots(orphanedIds, runContext);
        }
    }

    /**
     * Post-placement pass: adds a {@link SchedulingConflict} with reason
     * {@link SchedulingConflict.ReasonCode#NO_MATCHING_GAP} for every task that was not
     * placed on any day in the scheduling window.
     *
     * <p>This runs <em>after</em> the competitive placement loop has finished, so it captures
     * tasks that were eligible but could not find a positive-net-score slot anywhere in the
     * window — usually because the day is fully booked with higher-priority work.
     * Conflicts are consumed by the caller (typically a use case or a widget) to surface
     * diagnostic information to the user.
     */
    private void appendNoGapConflictsForWindow(List<Task> tasks,
                                               LocalDate startDay,
                                               int days,
                                               SchedulingRunContext runContext) {
        LocalDate endExclusive = startDay.plusDays(days);
        for (Task task : tasks) {
            if (task.core == null || task.core.id == null || task.core.completed) {
                continue;
            }
            boolean hasWindowSlot = false;
            for (TaskSlot slot : task.slots) {
                if (slot.day != null && !slot.day.isBefore(startDay) && slot.day.isBefore(endExclusive) && slot.scheduled) {
                    hasWindowSlot = true;
                    break;
                }
            }
            if (hasWindowSlot) {
                continue;
            }
            LocalDate conflictDay = task.core.fixedDate != null ? task.core.fixedDate : startDay;
            addConflict(task, conflictDay, NO_MATCHING_GAP,
                    "Keine passende Lücke im Planungsfenster gefunden");
        }
    }

    private void addConflict(Task task, LocalDate day, SchedulingConflict.ReasonCode reasonCode, String details) {
        SchedulingConflict conflict = new SchedulingConflict(
                task.core.id,
                task.core.title,
                day,
                reasonCode,
                details);
        currentRunContext.conflicts.add(conflict);
        log("[SCHED_CONFLICT] {taskId=" + conflict.taskId()
                + ", title=" + conflict.title()
                + ", day=" + conflict.day()
                + ", reasonCode=" + conflict.reasonCode()
                + ", details=" + conflict.details() + "}");
    }

    private void scheduleFixedTasks(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    List<OccupiedInterval> occupied,
                                    LocalDate day,
                                    SchedulingRunContext runContext) {
        List<Task> fixedTasks = collectFixedTasks(tasks);
        fixedTasks.sort(Comparator.comparing((Task t) -> t.core.fixedStart,
                Comparator.nullsLast(Comparator.naturalOrder())));

        for (Task task : fixedTasks) {
            if (task.core.fixedDate == null || !task.core.fixedDate.equals(day) || task.core.fixedStart == null) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(task.core.fixedDate, task.core.fixedStart);
            LocalDateTime end = computeFixedEnd(task, start);
            if (end == null || !end.isAfter(start) || start.isBefore(windowStart) || end.isAfter(windowEnd)) {
                addConflict(task, day, OUTSIDE_WINDOW, "Termin liegt außerhalb der Tagesgrenzen");
                continue;
            }
            Set<OccupiedInterval> overlaps = findOverlappingIntervals(occupied, start, end);
            if (!overlaps.isEmpty()) {
                boolean overlapsCalendar = false;
                for (OccupiedInterval overlap : overlaps) {
                    if (overlap.candidate == null) {
                        overlapsCalendar = true;
                        break;
                    }
                }
                addConflict(task, day, overlapsCalendar ? CALENDAR_OVERLAP : NO_MATCHING_GAP,
                        "Termin überschneidet belegte Zeit");
                continue;
            }
            TaskSlot slot = createScheduledSlot(task, start, FIXED_TASK_SCORE);
            slot.end = end.toLocalTime();
            slot.displacementScore = FIXED_TASK_SCORE;
            slot.displacementGroupType = TaskSlot.DisplacementGroupType.FIXED;
            slot.displacementGroupId = GROUP_PREFIX_FIXED + task.core.id;
            finalizeAssignment(task, slot);
            if (!task.children.isEmpty()) {
                scheduleChildrenWithinSlot(task.children, slot, start, end);
            }
            occupied.add(new OccupiedInterval(start, end, toCandidate(task, slot, false)));
            occupied.sort(Interval::compareTo);
        }
    }

    private List<Task> collectFixedTasks(List<Task> tasks) {
        List<Task> fixedTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.core.schedulingType == TaskCore.SchedulingType.TERMIN) {
                fixedTasks.add(task);
            }
            fixedTasks.addAll(collectFixedTasks(task.children));
        }
        return fixedTasks;
    }

    private LocalDateTime computeFixedEnd(Task task, LocalDateTime start) {
        if (task.core.fixedEnd != null) {
            return LocalDateTime.of(start.toLocalDate(), task.core.fixedEnd);
        }
        int duration = task.core.fixedDuration != null ? task.core.fixedDuration : task.core.plannedDurationMinutes();
        return duration > 0 ? start.plusMinutes(duration) : null;
    }

    /**
     * Returns the free time intervals within {@code [windowStart, windowEnd)} that are not
     * covered by any entry in {@code occupied}.
     *
     * <p><b>Precondition:</b> {@code occupied} must be sorted by {@link Interval#compareTo}
     * (start time ascending). All callers maintain this invariant via
     * {@code occupied.sort(Interval::compareTo)} after every mutation.
     */
    private List<Interval> findGaps(List<OccupiedInterval> occupied, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<Interval> gaps = new ArrayList<>();
        LocalDateTime cursor = windowStart;
        for (OccupiedInterval interval : occupied) {
            if (interval.start.isAfter(cursor)) {
                gaps.add(new Interval(cursor, interval.start));
            }
            if (interval.end.isAfter(cursor)) {
                cursor = interval.end;
            }
        }
        if (cursor.isBefore(windowEnd)) {
            gaps.add(new Interval(cursor, windowEnd));
        }
        return gaps;
    }

    /**
     * Collects all {@link LocalDateTime} values that the scheduler should try as a chain start.
     *
     * <p>Two kinds of start points are included:
     * <ol>
     *   <li><b>Gap starts</b> — the beginning of each free interval returned by {@link #findGaps}.
     *       Placing a chain here fills unused time without displacing anything.</li>
     *   <li><b>Displaceable interval starts</b> — the start time of every already-placed task slot
     *       that is eligible for eviction. Including these is the key to displacement: the algorithm
     *       can propose placing a higher-scoring chain at exactly the same time as a lower-scoring
     *       existing slot, and if the net score is positive, the existing slot is evicted.</li>
     * </ol>
     */
    private List<LocalDateTime> collectStartPoints(List<Interval> gaps, List<OccupiedInterval> occupied) {
        Set<LocalDateTime> starts = new HashSet<>();
        for (Interval gap : gaps) {
            starts.add(gap.start);
        }
        for (OccupiedInterval interval : occupied) {
            if (interval.isDisplaceable()) {
                starts.add(interval.start);
            }
        }
        List<LocalDateTime> sorted = new ArrayList<>(starts);
        sorted.sort(LocalDateTime::compareTo);
        return sorted;
    }

    private Set<OccupiedInterval> findOverlappingIntervals(List<OccupiedInterval> occupied, LocalDateTime start, LocalDateTime end) {
        Set<OccupiedInterval> result = new HashSet<>();
        for (OccupiedInterval interval : occupied) {
            if (start.isBefore(interval.end) && end.isAfter(interval.start)) {
                result.add(interval);
            }
        }
        return result;
    }

    /**
     * Expands a set of directly overlapping intervals to include all siblings that share the
     * same atomic group ID, then adds all resulting {@link DisplacementCandidate}s to
     * {@code expandedCandidates}.
     *
     * <p><b>Why atomicity matters:</b> when a prerequisite chain (A → B → C) was placed as a
     * unit, all three slots share the same {@code atomicGroupId}. If a new chain overlaps only
     * B, evicting just B would leave A and C dangling in an inconsistent state. This method
     * performs a BFS over {@code occupied} to find the full group and ensures that all members
     * are included in the eviction set — or none are (the caller returns {@code null} if any
     * member is non-displaceable).
     */
    private void expandToFullChains(Set<OccupiedInterval> overlapping,
                                    List<OccupiedInterval> occupied,
                                    Set<DisplacementCandidate> expandedCandidates) {
        ArrayDeque<OccupiedInterval> queue = new ArrayDeque<>(overlapping);
        Set<String> groupIds = new HashSet<>();

        while (!queue.isEmpty()) {
            OccupiedInterval interval = queue.poll();
            if (interval.candidate == null || interval.candidate.slot == null) {
                continue;
            }
            expandedCandidates.add(interval.candidate);
            String groupId = interval.candidate.atomicGroupId;
            if (groupId != null && groupIds.add(groupId)) {
                for (OccupiedInterval candidate : occupied) {
                    if (candidate.candidate != null && groupId.equals(candidate.candidate.atomicGroupId)) {
                        queue.add(candidate);
                    }
                }
            }
        }
    }

    /**
     * Builds the initial list of occupied intervals for {@code day} from existing task slots and
     * calendar events. This is called once per scheduling run before the placement loop begins.
     *
     * <p><b>Displacement eligibility:</b> a task slot is marked displaceable ({@code true}) only
     * if it is neither locked nor a fixed-time task:
     * <ul>
     *   <li><b>Locked</b> — {@code slot.completed || slot.realStart != null}. A slot is locked
     *       once the user has started or completed it; evicting it would destroy real work.</li>
     *   <li><b>Fixed-time (TERMIN)</b> — these slots are never moved by the normal competitive
     *       loop; they can only be displaced by other TERMIN slots (see
     *       {@link DisplacementCandidate#protectedFromNormalTasks}).</li>
     * </ul>
     * Calendar events are added as hard blocks ({@code candidate == null}) and are never displaceable.
     */
    private List<OccupiedInterval> collectOccupiedIntervals(List<Task> tasks,
                                                            LocalDate day,
                                                            List<CalendarBlockedIntervalProvider.BlockedInterval> blockedIntervals) {
        List<OccupiedInterval> intervals = new ArrayList<>();
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.day.equals(day) && slot.start != null && slot.end != null) {
                    boolean locked = slot.completed || slot.realStart != null;
                    boolean fixedTask = task.core.schedulingType == TaskCore.SchedulingType.TERMIN;
                    intervals.add(new OccupiedInterval(
                            day.atTime(slot.start),
                            day.atTime(slot.end),
                            toCandidate(task, slot, !locked && !fixedTask)));
                }
            }
        }
        for (CalendarBlockedIntervalProvider.BlockedInterval blockedInterval : blockedIntervals) {
            if (blockedInterval.start() == null
                    || blockedInterval.end() == null
                    || !blockedInterval.end().isAfter(blockedInterval.start())) {
                continue;
            }
            intervals.add(new OccupiedInterval(blockedInterval.start(), blockedInterval.end(), null));
        }
        intervals.sort(Interval::compareTo);
        return intervals;
    }

    private TaskSlot createScheduledSlot(Task task, LocalDateTime cursor, int score) {
        TaskSlot slot = new TaskSlot();
        slot.taskId = task.core.id;
        slot.score = score;
        slot.day = cursor.toLocalDate();
        slot.start = cursor.toLocalTime();
        slot.scheduled = true;
        return slot;
    }

    /**
     * Builds a {@link DisplacementCandidate} for {@code slot}, encoding the score and group ID
     * that the competitive loop needs to evaluate eviction.
     *
     * <p><b>Score priority:</b> if {@code slot.displacementScore != 0} (set by a prior placement),
     * it takes precedence over {@code slot.score}. This matters for chains: the first slot in a
     * chain stores the full chain's {@code gainScore} as its {@code displacementScore} so that
     * {@link #computeAtomicLoss} counts the full chain value exactly once.
     *
     * <p><b>Atomic group ID derivation</b> (evaluated in priority order):
     * <ol>
     *   <li>Use {@code slot.displacementGroupId} if already set (e.g. TERMIN slots use
     *       {@code "fixed:<taskId>"} assigned during {@link #scheduleFixedTasks}).</li>
     *   <li>Use {@code "chain:<chainId>"} if the slot belongs to a chain placement.</li>
     *   <li>Use {@code "slot:<slotId>"} for standalone single-slot placements.</li>
     * </ol>
     *
     * <p><b>{@code protectedFromNormalTasks}:</b> true for TERMIN slots. They can only be
     * displaced by another TERMIN slot (incoming chain contains a fixed-time task), never by
     * the normal competitive score comparison.
     */
    private DisplacementCandidate toCandidate(Task task, TaskSlot slot, boolean displaceable) {
        int score = slot.displacementScore != 0 ? slot.displacementScore : slot.score;
        String atomicGroupId = slot.displacementGroupId;
        if (atomicGroupId == null || atomicGroupId.isBlank()) {
            atomicGroupId = slot.chainId != null ? GROUP_PREFIX_CHAIN + slot.chainId : GROUP_PREFIX_SLOT + slot.id;
        }
        boolean fixedProtected = slot.displacementGroupType == TaskSlot.DisplacementGroupType.FIXED
                || task.core.schedulingType == TaskCore.SchedulingType.TERMIN;
        return new DisplacementCandidate(task, slot, displaceable, fixedProtected, score, atomicGroupId);
    }

    /**
     * Commits a newly created slot to the task model and updates the scorer's daily state.
     *
     * <p>Assigns {@code displacementGroupType} and {@code displacementGroupId} if not already
     * set (chain vs. single), appends the slot to {@code task.slots}, and calls
     * {@link TaskScorer#onSlotAssigned} to increment the daily repetition counter in the
     * scorer's cache. Failing to call this method after creating a slot would cause the scorer
     * to under-count today's placements, potentially scheduling more repetitions than
     * {@code repsPerDay} allows. The slot's {@code score} is expected to have been set
     * by the caller via {@link #createScheduledSlot} before this method is invoked.
     */
    private void finalizeAssignment(Task task, TaskSlot slot) {
        if (slot.displacementGroupType == null) {
            slot.displacementGroupType = slot.chainId != null ? TaskSlot.DisplacementGroupType.CHAIN : TaskSlot.DisplacementGroupType.SINGLE;
        }
        if (slot.displacementGroupId == null || slot.displacementGroupId.isBlank()) {
            slot.displacementGroupId = slot.chainId != null ? GROUP_PREFIX_CHAIN + slot.chainId : GROUP_PREFIX_SLOT + slot.id;
        }
        task.slots.add(slot);
        scorer.onSlotAssigned(task, slot.start);
        if (currentRunContext.planningState != null && slot.day != null) {
            currentRunContext.planningState.recordScheduled(task.core.id, slot.day);
        }
        currentRunContext.newSlots++;
    }

    /**
     * Recursively places child-task slots within a parent's time block.
     *
     * <p>Children are sorted by priority (highest first) and placed sequentially within
     * {@code [blockStart, blockEnd)}. Children that don't fit are skipped (not break),
     * so shorter children later in the list can still be placed. Grandchildren are
     * placed recursively within each child's sub-block.
     *
     * <p>Child slots are <em>not</em> added to the occupied-interval list because the
     * parent's {@link OccupiedInterval} already covers the entire block, protecting it
     * from displacement by unrelated tasks.
     *
     * <p>Each child slot inherits the parent's {@code displacementGroupId} and
     * {@code displacementGroupType} so that displacement of the parent atomically
     * removes all children (see {@link #removeOrphanedChildSlots}).
     */
    private void scheduleChildrenWithinSlot(List<Task> children, TaskSlot parentSlot,
                                            LocalDateTime blockStart, LocalDateTime blockEnd) {
        List<Task> sorted = new ArrayList<>(children);
        sorted.sort(Comparator.comparingInt((Task t) -> t.core.priority.scoringWeight).reversed());

        LocalDateTime cursor = blockStart;
        for (Task child : sorted) {
            if (child.core.completed) continue;
            if (child.core.schedulingType == TaskCore.SchedulingType.TERMIN) continue;

            int childDuration = child.core.plannedDurationMinutes();
            if (childDuration <= 0) continue;

            LocalDateTime childEnd = cursor.plusMinutes(childDuration);
            if (childEnd.isAfter(blockEnd)) continue;

            // maintenance() pre-computes scorer state (daily rep counters etc.) for this child.
            // Child inherits parent's score: children are never displaced individually,
            // only via parent eviction (displacementScore = 0).
            scorer.maintenance(child, parentSlot.day, currentRunContext.planningState);
            TaskSlot childSlot = createScheduledSlot(child, cursor, parentSlot.score);
            childSlot.end = childEnd.toLocalTime();
            childSlot.parent = parentSlot.id;
            childSlot.displacementGroupId = parentSlot.displacementGroupId;
            childSlot.displacementGroupType = parentSlot.displacementGroupType;
            childSlot.displacementScore = 0;
            finalizeAssignment(child, childSlot);
            if (!child.children.isEmpty()) {
                scheduleChildrenWithinSlot(child.children, childSlot, cursor, childEnd);
            }

            cursor = childEnd;
        }
    }

    private String formatSlot(TaskSlot slot) {
        String start = slot.start != null ? slot.start.format(HMM) : "?";
        String end = slot.end != null ? slot.end.format(HMM) : "?";
        return start + "-" + end + "(" + slot.score + ")";
    }

    /**
     * Finds the most recently ended scheduled or completed slot for {@code task} that finished
     * before {@code candidateStart}.
     *
     * <p>Used by {@link #hasUnmetPrerequisites} to check whether the prerequisite task has
     * already been placed at an acceptable position. A prerequisite is considered satisfied
     * when its slot's end time is at or before {@code candidateStart} plus the required gap.
     * Returns {@code null} if no such slot exists (prerequisite not yet placed).
     */
    private TaskSlot findLatestScheduledSlotBefore(Task task, LocalDateTime candidateStart) {
        TaskSlot best = null;
        LocalDateTime bestEnd = null;
        for (TaskSlot slot : task.slots) {
            if (slot.day == null || slot.end == null || (!slot.completed && !slot.scheduled)) {
                continue;
            }
            LocalDateTime slotEnd = LocalDateTime.of(slot.day, slot.end);
            if (slotEnd.isAfter(candidateStart)) {
                continue;
            }
            if (bestEnd == null || slotEnd.isAfter(bestEnd)) {
                best = slot;
                bestEnd = slotEnd;
            }
        }
        return best;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
