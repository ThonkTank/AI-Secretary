package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskTransitionStatLoader;
import com.autosecretary.features.task.domain.TaskTreeOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Internal scheduler that assigns tasks to time slots within a given window.
 */
public class DefaultTaskSlotGenerator implements TaskSlotGenerator {

    private static final SchedulingConflict.ReasonCode REASON_OUTSIDE_WINDOW = SchedulingConflict.ReasonCode.OUTSIDE_WINDOW;
    private static final SchedulingConflict.ReasonCode REASON_CALENDAR_OVERLAP = SchedulingConflict.ReasonCode.CALENDAR_OVERLAP;
    private static final SchedulingConflict.ReasonCode REASON_PREREQUISITE_BLOCKED = SchedulingConflict.ReasonCode.PREREQUISITE_BLOCKED;
    private static final SchedulingConflict.ReasonCode REASON_NO_MATCHING_GAP = SchedulingConflict.ReasonCode.NO_MATCHING_GAP;

    // Flat tiebreaker bonus per slot when evaluating prerequisite chains.
    // Favours longer chains being placed as a unit over individual task placement.
    private static final int CHAIN_COMPLETION_BONUS_PER_SLOT = 10;

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

    private static class DisplacementCandidate {
        final Task task;
        final TaskSlot slot;
        final boolean displaceable;
        final boolean protectedFromNormalTasks;
        final int lossScore;
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

    private static class ChainNode {
        final Task task;
        final int minGapFromPrevious;

        ChainNode(Task task, int minGapFromPrevious) {
            this.task = task;
            this.minGapFromPrevious = minGapFromPrevious;
        }
    }

    private static class ChainPlacement {
        final List<ChainNode> chain;
        final List<LocalDateTime> starts;
        final Set<DisplacementCandidate> toDisplace;
        final int gainScore;
        final int lossScore;
        final int netScore;
        final LocalDate day;

        ChainPlacement(List<ChainNode> chain,
                       List<LocalDateTime> starts,
                       Set<DisplacementCandidate> toDisplace,
                       int gainScore,
                       int lossScore,
                       LocalDate day) {
            this.chain = chain;
            this.starts = starts;
            this.toDisplace = toDisplace;
            this.gainScore = gainScore;
            this.lossScore = lossScore;
            this.netScore = gainScore - lossScore;
            this.day = day;
        }
    }

    private static class DaySchedulingContext {
        final LocalDate day;
        final LocalDateTime windowStart;
        final LocalDateTime windowEnd;
        final List<OccupiedInterval> occupied;

        DaySchedulingContext(LocalDate day,
                             LocalDateTime windowStart,
                             LocalDateTime windowEnd,
                             List<OccupiedInterval> occupied) {
            this.day = day;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.occupied = occupied;
        }
    }

    private static class SchedulingRunInit {
        final List<Task> taskTree;
        final List<Task> allTasks;

        SchedulingRunInit(List<Task> taskTree, List<Task> allTasks) {
            this.taskTree = taskTree;
            this.allTasks = allTasks;
        }
    }

    private static final SchedulingWindowProvider DEFAULT_WINDOW = SchedulingWindowProvider.DEFAULT;

    private final Consumer<String> logger;
    private final SchedulingWindowProvider schedulingWindowProvider;
    private final CalendarBlockedIntervalProvider calendarBlockedIntervalProvider;
    private final TaskScorer scorer;
    private final TaskTransitionStatLoader transitionStatLoader;
    private final List<SchedulingConflict> lastConflicts = new ArrayList<>();

    private int newSlots;
    private Map<String, Task> allTasksById;
    private LocalDate schedulingDay;
    private TaskPlanningState planningState;

    private static final DateTimeFormatter HMM = DateTimeFormatter.ofPattern("HH:mm");

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, null, DEFAULT_WINDOW, CalendarBlockedIntervalProvider.NONE);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager, Consumer<String> logger) {
        this(lifecycleManager, logger, DEFAULT_WINDOW, CalendarBlockedIntervalProvider.NONE);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager,
                                    Consumer<String> logger,
                                    SchedulingWindowProvider schedulingWindowProvider,
                                    CalendarBlockedIntervalProvider calendarBlockedIntervalProvider) {
        this(lifecycleManager, logger, schedulingWindowProvider, calendarBlockedIntervalProvider, null, null);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager,
                                    Consumer<String> logger,
                                    SchedulingWindowProvider schedulingWindowProvider,
                                    CalendarBlockedIntervalProvider calendarBlockedIntervalProvider,
                                    TaskTransitionStatLoader transitionStatLoader) {
        this(lifecycleManager, logger, schedulingWindowProvider, calendarBlockedIntervalProvider, transitionStatLoader, null);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager,
                                    Consumer<String> logger,
                                    SchedulingWindowProvider schedulingWindowProvider,
                                    CalendarBlockedIntervalProvider calendarBlockedIntervalProvider,
                                    TaskTransitionStatLoader transitionStatLoader,
                                    TaskBudgetEligibilityService taskBudgetEligibilityService) {
        this.scorer = new TaskScorer(lifecycleManager, logger, taskBudgetEligibilityService);
        this.transitionStatLoader = transitionStatLoader;
        this.logger = logger;
        this.schedulingWindowProvider = schedulingWindowProvider;
        this.calendarBlockedIntervalProvider = calendarBlockedIntervalProvider;
    }

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

    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDateTime windowStart, LocalDateTime windowEnd, TaskPlanningState state) {
        return generateSlotsForDay(tasks, windowStart, windowEnd, state, new ArrayList<>());
    }

    @Override
    public TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) {
        SchedulingWindowProvider.SchedulingWindow window = schedulingWindowProvider.forDay(day);
        return generateSlotsForDayInternal(tasks, window.start(), window.end(), state, new ArrayList<>());
    }

    @Override
    public TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay, int days, TaskPlanningState state) {
        if (days <= 0) {
            return new TaskSlotGenerationResult(0, new ArrayList<>());
        }

        SchedulingRunInit init = initSchedulingRun(tasks, state);
        List<Task> taskTree = init.taskTree;
        List<Task> allTasks = init.allTasks;

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
            scheduleFixedTasks(taskTree, window.start(), window.end(), occupied, day);
            contexts.add(new DaySchedulingContext(day, window.start(), window.end(), occupied));
        }

        assignGlobalBestFitAcrossWindow(taskTree, contexts);
        appendNoGapConflictsForWindow(allTasks, startDay, days);
        logWindowSummary(allTasks, contexts);
        return new TaskSlotGenerationResult(newSlots, lastConflicts);
    }

    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    TaskPlanningState state,
                                    List<TaskCalendarEvent> calendarEvents) {
        return generateSlotsForDayInternal(tasks, windowStart, windowEnd, state, calendarEvents);
    }

    private TaskSlotGenerationResult generateSlotsForDayInternal(List<Task> tasks,
                                             LocalDateTime windowStart,
                                             LocalDateTime windowEnd,
                                             TaskPlanningState state,
                                             List<TaskCalendarEvent> calendarEvents) {
        schedulingDay = windowStart.toLocalDate();
        SchedulingRunInit init = initSchedulingRun(tasks, state);
        List<Task> taskTree = init.taskTree;
        List<Task> allTasks = init.allTasks;

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

        List<OccupiedInterval> occupied = collectOccupiedIntervals(allTasks, schedulingDay, calendarEvents);
        for (CalendarBlockedIntervalProvider.BlockedInterval blocked :
                calendarBlockedIntervalProvider.readBlockedIntervals(schedulingDay, windowStart, windowEnd)) {
            occupied.add(new OccupiedInterval(blocked.start(), blocked.end(), null));
        }
        occupied.sort(Interval::compareTo);

        scheduleFixedTasks(taskTree, windowStart, windowEnd, occupied, windowStart.toLocalDate());
        assignGlobalBestFit(taskTree, windowStart, windowEnd, occupied);

        int totalDaySlots = logDaySummary(allTasks, schedulingDay, true);
        log("Gesamt: " + totalDaySlots + " slots (neu: " + newSlots + ")");
        appendNoGapConflictsForWindow(allTasks, schedulingDay, 1);
        return new TaskSlotGenerationResult(newSlots, lastConflicts);
    }

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
        newSlots = 0;
        scorer.reset();
        scorer.setTransitionStats(transitionStatLoader != null ? transitionStatLoader.load() : new ArrayList<>());
        lastConflicts.clear();
        planningState = state;
        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);
        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);
        allTasksById = new HashMap<>();
        for (Task t : allTasks) {
            allTasksById.put(t.core.id, t);
        }
        return new SchedulingRunInit(taskTree, allTasks);
    }

    private void assignGlobalBestFit(List<Task> tasks,
                                     LocalDateTime windowStart,
                                     LocalDateTime windowEnd,
                                     List<OccupiedInterval> occupied) {
        List<DaySchedulingContext> singleDay = new ArrayList<>();
        singleDay.add(new DaySchedulingContext(windowStart.toLocalDate(), windowStart, windowEnd, occupied));
        assignGlobalBestFitAcrossWindow(tasks, singleDay);
    }

    /** Safety cap for the placement loop — prevents infinite scheduling if a bug prevents convergence. */
    private static final int MAX_PLACEMENT_ITERATIONS = 10_000;

    private void assignGlobalBestFitAcrossWindow(List<Task> tasks, List<DaySchedulingContext> contexts) {
        int iterations = 0;
        while (iterations < MAX_PLACEMENT_ITERATIONS) {
            iterations++;
            List<List<ChainNode>> chains = buildTaskChains(tasks);
            ChainPlacement best = null;
            DaySchedulingContext bestContext = null;

            for (DaySchedulingContext context : contexts) {
                List<LocalDateTime> startPoints = collectStartPoints(
                        findGaps(context.occupied, context.windowStart, context.windowEnd),
                        context.occupied);
                if (startPoints.isEmpty()) {
                    continue;
                }
                ChainPlacement dayBest = null;
                for (List<ChainNode> chain : chains) {
                    ChainPlacement chainBest = evaluateChainCandidates(chain, startPoints, context.windowEnd, context.occupied);
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
            applyPlacement(best, bestContext.occupied);
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

        StringBuilder displaced = new StringBuilder();
        for (DisplacementCandidate candidate : placement.toDisplace) {
            TaskSlot slot = candidate.slot;
            Task owner = candidate.task != null ? candidate.task : allTasksById.get(slot.taskId);
            if (displaced.length() > 0) {
                displaced.append(", ");
            }
            displaced.append(owner != null ? owner.core.title : slot.taskId)
                    .append("[")
                    .append(slot.day)
                    .append(" ")
                    .append(slot.start != null ? slot.start.format(HMM) : "?")
                    .append("-")
                    .append(slot.end != null ? slot.end.format(HMM) : "?")
                    .append("]");
        }

        log("[GLOBAL-COMPETE] day=" + context.day
                + " winner=" + chainSummary
                + " gain=" + placement.gainScore
                + " loss=" + placement.lossScore
                + " net=" + placement.netScore
                + (displaced.length() > 0 ? " verdrängt=" + displaced : " verdrängt=keine"));
    }

    private void logWindowSummary(List<Task> allTasks, List<DaySchedulingContext> contexts) {
        for (DaySchedulingContext context : contexts) {
            int totalDaySlots = logDaySummary(allTasks, context.day, false);
            log("Gesamt: " + totalDaySlots + " slots");
        }
        log("Global neu eingeplant: " + newSlots + " slots");
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
                for (TaskSlot slot : daySlots) {
                    if (summary.length() > 0) summary.append(", ");
                    summary.append(formatSlot(slot));
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
                                                   List<OccupiedInterval> occupied) {
        ChainPlacement best = null;
        for (LocalDateTime start : startPoints) {
            for (int len = 1; len <= fullChain.size(); len++) {
                List<ChainNode> fitting = fullChain.subList(0, len);
                ChainPlacement candidate = tryPlaceChain(fitting, start, windowEnd, occupied);
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

    private ChainPlacement tryPlaceChain(List<ChainNode> chain,
                                         LocalDateTime firstStart,
                                         LocalDateTime windowEnd,
                                         List<OccupiedInterval> occupied) {
        List<LocalDateTime> starts = new ArrayList<>();
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
            scorer.maintenance(task, cursor.toLocalDate(), planningState != null ? planningState : new TaskPlanningState());
            int score = scorer.score(task, cursor, end, findPreviousTaskIdForContext(cursor, starts, chain, i, occupied));
            if (score <= 0) {
                return null;
            }
            gain += score;
            starts.add(cursor);
            cursor = end;
        }

        gain += chain.size() * CHAIN_COMPLETION_BONUS_PER_SLOT;
        int loss = computeAtomicLoss(toDisplace);
        if (gain - loss <= 0) {
            return null;
        }

        return new ChainPlacement(new ArrayList<>(chain), starts, toDisplace, gain, loss, firstStart.toLocalDate());
    }

    private String findPreviousTaskIdForContext(LocalDateTime candidateStart,
                                                List<LocalDateTime> chainStarts,
                                                List<ChainNode> chain,
                                                int currentIndex,
                                                List<OccupiedInterval> occupied) {
        LocalDateTime latest = null;
        String taskId = null;

        for (int i = 0; i < currentIndex; i++) {
            LocalDateTime chainStart = chainStarts.get(i);
            if (!chainStart.isAfter(candidateStart) && (latest == null || chainStart.isAfter(latest))) {
                latest = chainStart;
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

    private int computeAtomicLoss(Set<DisplacementCandidate> candidates) {
        int loss = 0;
        Set<String> seenGroups = new HashSet<>();
        for (DisplacementCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate.atomicGroupId != null) {
                if (seenGroups.add(candidate.atomicGroupId)) {
                    loss += Math.max(0, candidate.lossScore);
                }
            } else {
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
            Task prereqTask = allTasksById.get(prereq.prerequisiteId);
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
                    if (!n.task.core.id.equals(prereq.prerequisiteId)) {
                        continue;
                    }
                    LocalDateTime start = chainStarts.get(i);
                    prereqEnd = start.plusMinutes(n.task.core.plannedDurationMinutes());
                    break;
                }
            }

            if (prereqEnd == null) {
                addConflict(task, candidateStart.toLocalDate(), REASON_PREREQUISITE_BLOCKED,
                        "Vorgängeraufgabe noch nicht geplant: " + prereq.prerequisiteId);
                return true;
            }

            LocalDateTime earliestStart = prereqEnd.plusMinutes(Math.max(0, prereq.minGapMinutes));
            if (candidateStart.isBefore(earliestStart)) {
                addConflict(task, candidateStart.toLocalDate(), REASON_PREREQUISITE_BLOCKED,
                        "Frühester Start nach Prerequisite: " + earliestStart.toLocalTime());
                return true;
            }
        }
        return false;
    }

    private List<List<ChainNode>> buildTaskChains(List<Task> tasks) {
        Map<String, List<ChainNode>> outgoing = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (Task task : tasks) {
            if (task.prerequisites == null) {
                continue;
            }
            for (TaskPrerequisite prerequisite : task.prerequisites) {
                if (!allTasksById.containsKey(prerequisite.prerequisiteId)) {
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

    private void applyPlacement(ChainPlacement placement, List<OccupiedInterval> occupied) {
        removeDisplacedSlots(placement.toDisplace, occupied);
        String chainId = placement.chain.size() > 1 ? UUID.randomUUID().toString() : null;

        for (int i = 0; i < placement.chain.size(); i++) {
            Task task = placement.chain.get(i).task;
            LocalDateTime start = placement.starts.get(i);
            TaskSlot slot = createScheduledSlot(task, start, placement.gainScore / placement.chain.size(), null);
            int plannedDuration = task.core.plannedDurationMinutes();
            slot.end = start.plusMinutes(plannedDuration).toLocalTime();
            slot.chainId = chainId;
            finalizeAssignment(task, slot, slot.score);
            if (planningState != null) {
                planningState.recordScheduled(task.core.id, slot.day);
            }
            occupied.add(new OccupiedInterval(start, start.plusMinutes(plannedDuration), toCandidate(task, slot, true)));
        }
        occupied.sort(Interval::compareTo);
    }

    private void removeDisplacedSlots(Set<DisplacementCandidate> displaced, List<OccupiedInterval> occupied) {
        if (displaced.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (DisplacementCandidate candidate : displaced) {
            TaskSlot slot = candidate.slot;
            ids.add(slot.id);
            Task owner = allTasksById.get(slot.taskId);
            if (owner != null) {
                owner.slots.removeIf(existing -> existing.id.equals(slot.id));
            }
            if (planningState != null && slot.day != null) {
                planningState.removeScheduled(slot.taskId, slot.day);
            }
        }
        occupied.removeIf(interval -> interval.candidate != null
                && interval.candidate.slot != null
                && ids.contains(interval.candidate.slot.id));
    }

    public List<SchedulingConflict> getLastConflicts() {
        return new ArrayList<>(lastConflicts);
    }

    private void appendNoGapConflictsForWindow(List<Task> tasks, LocalDate startDay, int days) {
        LocalDate endExclusive = startDay.plusDays(days);
        for (Task task : tasks) {
            if (task.core == null || task.core.id == null || task.core.completed) {
                continue;
            }
            boolean hasWindowSlot = task.slots.stream().anyMatch(slot ->
                    slot.day != null
                            && !slot.day.isBefore(startDay)
                            && slot.day.isBefore(endExclusive)
                            && slot.scheduled);
            if (hasWindowSlot) {
                continue;
            }
            LocalDate conflictDay = task.core.fixedDate != null ? task.core.fixedDate : startDay;
            addConflict(task, conflictDay, REASON_NO_MATCHING_GAP,
                    "Keine passende Lücke im Planungsfenster gefunden");
        }
    }

    private void addConflict(Task task, LocalDate day, SchedulingConflict.ReasonCode reasonCode, String details) {
        SchedulingConflict conflict = new SchedulingConflict(
                task.core != null ? task.core.id : null,
                task.core != null ? task.core.title : "",
                day,
                reasonCode,
                details);
        lastConflicts.add(conflict);
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
                                    LocalDate day) {
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
                addConflict(task, day, REASON_OUTSIDE_WINDOW, "Termin liegt außerhalb der Tagesgrenzen");
                continue;
            }
            Set<OccupiedInterval> overlaps = findOverlappingIntervals(occupied, start, end);
            if (!overlaps.isEmpty()) {
                boolean overlapsCalendar = overlaps.stream().anyMatch(interval -> interval.candidate == null);
                addConflict(task, day, overlapsCalendar ? REASON_CALENDAR_OVERLAP : REASON_OUTSIDE_WINDOW,
                        "Termin überschneidet belegte Zeit");
                continue;
            }
            TaskSlot slot = createScheduledSlot(task, start, Integer.MAX_VALUE / 2, null);
            slot.end = end.toLocalTime();
            slot.displacementScore = Integer.MAX_VALUE / 2;
            slot.displacementGroupType = TaskSlot.DisplacementGroupType.FIXED;
            slot.displacementGroupId = "fixed:" + task.core.id;
            finalizeAssignment(task, slot, Integer.MAX_VALUE / 2);
            if (planningState != null) {
                planningState.recordScheduled(task.core.id, slot.day);
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
            if (task.children != null && !task.children.isEmpty()) {
                fixedTasks.addAll(collectFixedTasks(task.children));
            }
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

    private List<OccupiedInterval> collectOccupiedIntervals(List<Task> tasks, LocalDate day, List<TaskCalendarEvent> calendarEvents) {
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
        for (TaskCalendarEvent event : calendarEvents) {
            if (event.start() == null || event.end() == null || !event.end().isAfter(event.start())) {
                continue;
            }
            intervals.add(new OccupiedInterval(day.atTime(event.start()), day.atTime(event.end()), null));
        }
        intervals.sort(Interval::compareTo);
        return intervals;
    }

    private TaskSlot createScheduledSlot(Task task, LocalDateTime cursor, int score, TaskSlot parentSlot) {
        TaskSlot slot = new TaskSlot();
        slot.taskId = task.core.id;
        slot.score = score;
        slot.day = cursor.toLocalDate();
        slot.start = cursor.toLocalTime();
        slot.scheduled = true;
        slot.parent = parentSlot != null ? parentSlot.id : null;
        return slot;
    }

    private DisplacementCandidate toCandidate(Task task, TaskSlot slot, boolean displaceable) {
        int score = slot.displacementScore != 0 ? slot.displacementScore : slot.score;
        String atomicGroupId = slot.displacementGroupId;
        if (atomicGroupId == null || atomicGroupId.isBlank()) {
            atomicGroupId = slot.chainId != null ? "chain:" + slot.chainId : "slot:" + slot.id;
        }
        boolean fixedProtected = slot.displacementGroupType == TaskSlot.DisplacementGroupType.FIXED
                || task.core.schedulingType == TaskCore.SchedulingType.TERMIN;
        return new DisplacementCandidate(task, slot, displaceable, fixedProtected, score, atomicGroupId);
    }

    private void finalizeAssignment(Task task, TaskSlot slot, int score) {
        slot.score = score;
        slot.displacementScore = score;
        if (slot.displacementGroupType == null) {
            slot.displacementGroupType = slot.chainId != null ? TaskSlot.DisplacementGroupType.CHAIN : TaskSlot.DisplacementGroupType.SINGLE;
        }
        if (slot.displacementGroupId == null || slot.displacementGroupId.isBlank()) {
            slot.displacementGroupId = slot.chainId != null ? "chain:" + slot.chainId : "slot:" + slot.id;
        }
        task.slots.add(slot);
        scorer.onSlotAssigned(task, slot.start);
        newSlots++;
    }

    private String formatSlot(TaskSlot slot) {
        String start = slot.start != null ? slot.start.format(HMM) : "?";
        String end = slot.end != null ? slot.end.format(HMM) : "?";
        return start + "-" + end + "(" + slot.score + ")";
    }

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
