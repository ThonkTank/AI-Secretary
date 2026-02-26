package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskPlanningState;
import com.autosecretary.features.task.domain.TaskTreeOperations;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
public class DefaultTaskSlotGenerator {


    private static class PlannedSlot {
        private final Task task;
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final int score;
        private final int chainOrder;

        private PlannedSlot(Task task, LocalDateTime start, LocalDateTime end, int score, int chainOrder) {
            this.task = task;
            this.start = start;
            this.end = end;
            this.score = score;
            this.chainOrder = chainOrder;
        }
    }

    private static class ChainPlan {
        private final String chainId;
        private final List<PlannedSlot> plannedSlots;
        private final int score;
        private final String logLabel;

        private ChainPlan(String chainId, List<PlannedSlot> plannedSlots, int score, String logLabel) {
            this.chainId = chainId;
            this.plannedSlots = plannedSlots;
            this.score = score;
            this.logLabel = logLabel;
        }
    }

    private static class CandidateSelection {
        private final ChainPlan chainPlan;
        private final String scoreLog;

        private CandidateSelection(ChainPlan chainPlan, String scoreLog) {
            this.chainPlan = chainPlan;
            this.scoreLog = scoreLog;
        }
    }

    private static class ScoreLogBuilder {
        private final StringBuilder scores = new StringBuilder();

        private void appendEntry(String label, int score, String suffix) {
            appendSeparatorIfNeeded();
            scores.append(label).append(": ").append(score);
            if (suffix != null && !suffix.isEmpty()) {
                scores.append(' ').append(suffix);
            }
        }

        private String build() {
            return scores.toString();
        }

        private void appendSeparatorIfNeeded() {
            if (scores.length() > 0) {
                scores.append("  |  ");
            }
        }
    }

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

    private final Consumer<String> logger;
    private final SchedulingWindowProvider schedulingWindowProvider;
    private final CalendarBlockedIntervalProvider calendarBlockedIntervalProvider;
    private int newSlots;
    private Set<String> scheduledInSession;
    private Map<String, Task> allTasksById;
    private final TaskScorer scorer;
    private LocalDate schedulingDay;
    private long availableBudgetCents = Long.MAX_VALUE;

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager) {
        this(lifecycleManager, null, day -> {
            LocalDateTime start = LocalDateTime.of(day, java.time.LocalTime.of(6, 0));
            LocalDateTime end = LocalDateTime.of(day, java.time.LocalTime.of(21, 0));
            return new SchedulingWindowProvider.SchedulingWindow(start, end);
        }, CalendarBlockedIntervalProvider.NONE);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager, Consumer<String> logger) {
        this(lifecycleManager, logger, day -> {
            LocalDateTime start = LocalDateTime.of(day, java.time.LocalTime.of(6, 0));
            LocalDateTime end = LocalDateTime.of(day, java.time.LocalTime.of(21, 0));
            return new SchedulingWindowProvider.SchedulingWindow(start, end);
        }, CalendarBlockedIntervalProvider.NONE);
    }

    public DefaultTaskSlotGenerator(TaskLifecycleManager lifecycleManager,
                                    Consumer<String> logger,
                                    SchedulingWindowProvider schedulingWindowProvider,
                                    CalendarBlockedIntervalProvider calendarBlockedIntervalProvider) {
        this.scorer = new TaskScorer(lifecycleManager);
        this.logger = logger;
        this.schedulingWindowProvider = schedulingWindowProvider;
        this.calendarBlockedIntervalProvider = calendarBlockedIntervalProvider;
    }

    private static final DateTimeFormatter HMM = DateTimeFormatter.ofPattern("HH:mm");

    public TaskPlanningState createPlanningState() {
        return new TaskPlanningState();
    }

    public void setAvailableBudgetCents(long availableBudgetCents) {
        this.availableBudgetCents = Math.max(0L, availableBudgetCents);
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

    public void generateSlotsForDay(List<Task> tasks, LocalDateTime windowStart, LocalDateTime windowEnd, TaskPlanningState state) {
        generateSlotsForDay(tasks, windowStart, windowEnd, state, new ArrayList<>());
    }

    public void generateSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) {
        SchedulingWindowProvider.SchedulingWindow window = schedulingWindowProvider.forDay(day);
        LocalDateTime windowStart = window.start;
        LocalDateTime windowEnd = window.end;
        generateSlotsForDayInternal(tasks, windowStart, windowEnd, state, new ArrayList<>());
    }

    public void generateSlotsForDay(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    TaskPlanningState state,
                                    List<CalendarEvent> calendarEvents) {
        generateSlotsForDayInternal(tasks, windowStart, windowEnd, state, calendarEvents);
    }

    private void generateSlotsForDayInternal(List<Task> tasks,
                                             LocalDateTime windowStart,
                                             LocalDateTime windowEnd,
                                             TaskPlanningState state,
                                             List<CalendarEvent> calendarEvents) {
        schedulingDay = windowStart.toLocalDate();
        newSlots = 0;
        scorer.reset();

        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);
        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);

        scheduledInSession = new HashSet<>();
        allTasksById = new HashMap<>();
        for (Task t : allTasks) {
            allTasksById.put(t.core.id, t);
            scorer.maintenance(t, schedulingDay, state);
        }

        for (Task t : allTasks) {
            for (TaskSlot slot : t.slots) {
                if (slot.day.equals(schedulingDay) && (slot.completed || slot.realStart != null)) {
                    scheduledInSession.add(t.core.id);
                    scorer.onSlotAssigned(t, slot.start);
                }
            }
        }

        long windowMin = ChronoUnit.MINUTES.between(windowStart, windowEnd);
        log("=== Generierung " + schedulingDay + " === Fenster " + windowStart.format(HMM) + "-" + windowEnd.format(HMM) + " (" + windowMin + "min), " + taskTree.size() + " root tasks");

        List<Interval> occupied = collectOccupiedIntervals(allTasks, schedulingDay, calendarEvents);
        for (CalendarBlockedIntervalProvider.BlockedInterval blocked :
                calendarBlockedIntervalProvider.readBlockedIntervals(schedulingDay, windowStart, windowEnd)) {
            occupied.add(new Interval(blocked.start, blocked.end));
        }
        occupied.sort(Interval::compareTo);
        scheduleFixedAppointments(taskTree, windowStart, windowEnd, occupied);
        assignGlobalBestFit(taskTree, windowStart, windowEnd, null, 0, occupied);

        log("=== Zusammenfassung " + schedulingDay + " ===");
        int totalDaySlots = 0;
        for (Task t : allTasks) {
            List<TaskSlot> daySlots = new ArrayList<>();
            for (TaskSlot s : t.slots) {
                if (s.day.equals(schedulingDay) && s.scheduled) daySlots.add(s);
            }
            if (!daySlots.isEmpty()) {
                totalDaySlots += daySlots.size();
                StringBuilder summary = new StringBuilder();
                for (TaskSlot slot : daySlots) {
                    if (summary.length() > 0) {
                        summary.append(", ");
                    }
                    summary.append(formatSlot(slot));
                }
                log("  " + t.core.title + ": " + daySlots.size() + " slots [" + summary + "]");
            } else {
                log("  " + t.core.title + ": unscheduled");
            }
        }
        log("Gesamt: " + totalDaySlots + " slots (neu: " + newSlots + "), Restbudget=" + availableBudgetCents + "c");
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

    private void scheduleFixedAppointments(List<Task> tasks,
                                           LocalDateTime windowStart,
                                           LocalDateTime windowEnd,
                                           List<Interval> occupied) {
        for (Task task : TaskTreeOperations.flatten(tasks)) {
            if (!isFixedForSchedulingDay(task)) {
                continue;
            }
            placeFixedAppointment(task, windowStart, windowEnd, occupied);
        }
    }

    private boolean isFixedForSchedulingDay(Task task) {
        if (task.core == null || !task.core.isFixedAppointment) {
            return false;
        }
        return schedulingDay.equals(task.core.fixedDate) && task.core.fixedStart != null;
    }

    private void placeFixedAppointment(Task task,
                                       LocalDateTime windowStart,
                                       LocalDateTime windowEnd,
                                       List<Interval> occupied) {
        if (hasScheduledSlotForDay(task, schedulingDay)) {
            return;
        }

        int durationMinutes = resolveFixedDurationMinutes(task);
        LocalDateTime fixedStart = LocalDateTime.of(schedulingDay, task.core.fixedStart);
        LocalDateTime fixedEnd = fixedStart.plusMinutes(durationMinutes);

        TaskSlot slot = createScheduledSlot(task, fixedStart, Integer.MAX_VALUE, null);
        slot.end = fixedEnd.toLocalTime();
        finalizeAssignment(task, slot, Integer.MAX_VALUE);

        boolean outsideWindow = fixedStart.isBefore(windowStart) || fixedEnd.isAfter(windowEnd);
        boolean hasCollision = hasCollision(occupied, fixedStart, fixedEnd);

        if (outsideWindow || hasCollision) {
            log("  ⚠ Fixtermin-Konflikt: " + task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "]");
        } else {
            log("  ✓ Fixtermin gesetzt: " + task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "]");
        }

        insertSorted(occupied, new Interval(fixedStart, fixedEnd));
    }

    private boolean hasScheduledSlotForDay(Task task, LocalDate day) {
        for (TaskSlot slot : task.slots) {
            if (slot.day != null && slot.day.equals(day) && slot.scheduled) {
                return true;
            }
        }
        return false;
    }

    private int resolveFixedDurationMinutes(Task task) {
        if (task.core.fixedDurationMinutes != null && task.core.fixedDurationMinutes > 0) {
            return task.core.fixedDurationMinutes;
        }
        if (task.core.maxDuration > 0) {
            return task.core.maxDuration;
        }
        return 1;
    }

    private boolean hasCollision(List<Interval> occupied, LocalDateTime start, LocalDateTime end) {
        for (Interval interval : occupied) {
            if (start.isBefore(interval.end) && end.isAfter(interval.start)) {
                return true;
            }
        }
        return false;
    }

    private void assignGlobalBestFit(List<Task> tasks, LocalDateTime windowStart, LocalDateTime windowEnd,
                                     TaskSlot parentSlot, int depth, List<Interval> occupied) {
        String indent = "  ".repeat(depth);
        Set<String> budgetBlockedTaskIds = new HashSet<>();

        while (true) {
            List<Interval> gaps = findGaps(occupied, windowStart, windowEnd);
            if (gaps.isEmpty()) {
                log(indent + "--- Keine Lücken übrig [depth=" + depth + "] ---");
                break;
            }

            long totalFreeMin = 0;
            for (Interval gap : gaps) {
                totalFreeMin += ChronoUnit.MINUTES.between(gap.start, gap.end);
            }
            log(indent + "--- Lückensuche [depth=" + depth + "], " + gaps.size() + " Lücken, " + totalFreeMin + "min frei ---");

            List<Task> eligibleTasks;
            if (budgetBlockedTaskIds.isEmpty()) {
                eligibleTasks = tasks;
            } else {
                eligibleTasks = new ArrayList<>();
                for (Task t : tasks) {
                    if (!budgetBlockedTaskIds.contains(t.core.id)) {
                        eligibleTasks.add(t);
                    }
                }
            }
            CandidateSelection selection = evaluateAllCandidates(eligibleTasks, gaps, windowEnd);
            log(indent + "  " + selection.scoreLog);

            if (selection.chainPlan == null || selection.chainPlan.score <= 0) {
                log(indent + "  → (keine Task/Chain qualifiziert, Abbruch)");
                break;
            }

            long chainBudgetCents = 0L;
            for (PlannedSlot ps : selection.chainPlan.plannedSlots) {
                chainBudgetCents += Math.max(0L, ps.task.core.budgetRequirementCents);
            }
            if (chainBudgetCents > availableBudgetCents) {
                log(indent + "  ↷ skip chain (Budget: benötigt=" + chainBudgetCents + "c, rest=" + availableBudgetCents + "c)");
                for (PlannedSlot ps : selection.chainPlan.plannedSlots) {
                    budgetBlockedTaskIds.add(ps.task.core.id);
                }
                continue;
            }
            commitChainPlan(selection.chainPlan, parentSlot, depth, occupied, indent);
            availableBudgetCents = Math.max(0L, availableBudgetCents - chainBudgetCents);
        }
    }

    private CandidateSelection evaluateAllCandidates(List<Task> tasks,
                                                     List<Interval> gaps,
                                                     LocalDateTime windowEnd) {
        ScoreLogBuilder scoreLogBuilder = new ScoreLogBuilder();
        List<List<Task>> schedulingUnits = resolveChainUnits(tasks);

        ChainPlan bestPlan = null;
        for (List<Task> unit : schedulingUnits) {
            ChainPlan unitBestPlan = null;
            for (Interval gap : gaps) {
                LocalDateTime gapEnd = gap.end.isBefore(windowEnd) ? gap.end : windowEnd;
                ChainPlan plan = simulateChainPlan(unit, gap.start, gapEnd);
                if (plan == null) {
                    continue;
                }
                if (unitBestPlan == null || plan.score > unitBestPlan.score) {
                    unitBestPlan = plan;
                }
                if (bestPlan == null || plan.score > bestPlan.score) {
                    bestPlan = plan;
                }
            }

            String label = unit.size() == 1 ? unit.get(0).core.title : "Chain(" + unit.size() + "):" + unit.get(0).core.title;
            scoreLogBuilder.appendEntry(label, unitBestPlan != null ? unitBestPlan.score : 0,
                    unitBestPlan != null ? unitBestPlan.logLabel : "(kein Fit)");
        }

        return new CandidateSelection(bestPlan, scoreLogBuilder.build());
    }

    private List<List<Task>> resolveChainUnits(List<Task> tasks) {
        Map<String, Task> taskMap = new HashMap<>();
        for (Task task : tasks) {
            taskMap.put(task.core.id, task);
        }

        Map<String, Set<String>> undirected = new HashMap<>();
        for (Task task : tasks) {
            undirected.put(task.core.id, new HashSet<>());
        }

        for (Task task : tasks) {
            if (task.prerequisites == null) {
                continue;
            }
            for (TaskPrerequisite prereq : task.prerequisites) {
                if (!taskMap.containsKey(prereq.prerequisiteId)) {
                    continue;
                }
                undirected.get(task.core.id).add(prereq.prerequisiteId);
                undirected.get(prereq.prerequisiteId).add(task.core.id);
            }
        }

        List<List<Task>> units = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (Task task : tasks) {
            if (visited.contains(task.core.id)) {
                continue;
            }
            List<Task> component = new ArrayList<>();
            collectComponent(task.core.id, taskMap, undirected, visited, component);

            if (component.size() == 1) {
                units.add(component);
            } else {
                units.add(orderChainComponent(component));
            }
        }

        units.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return units;
    }

    private void collectComponent(String startId,
                                  Map<String, Task> taskMap,
                                  Map<String, Set<String>> undirected,
                                  Set<String> visited,
                                  List<Task> out) {
        List<String> queue = new ArrayList<>();
        queue.add(startId);
        visited.add(startId);

        for (int i = 0; i < queue.size(); i++) {
            String current = queue.get(i);
            Task currentTask = taskMap.get(current);
            if (currentTask != null) {
                out.add(currentTask);
            }
            for (String next : undirected.getOrDefault(current, Collections.emptySet())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private List<Task> orderChainComponent(List<Task> component) {
        Map<String, Task> componentMap = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (Task task : component) {
            componentMap.put(task.core.id, task);
            indegree.put(task.core.id, 0);
            outgoing.put(task.core.id, new ArrayList<>());
        }

        for (Task task : component) {
            if (task.prerequisites == null) {
                continue;
            }
            for (TaskPrerequisite prereq : task.prerequisites) {
                if (!componentMap.containsKey(prereq.prerequisiteId)) {
                    continue;
                }
                outgoing.get(prereq.prerequisiteId).add(task.core.id);
                indegree.put(task.core.id, indegree.get(task.core.id) + 1);
            }
        }

        List<Task> ordered = new ArrayList<>();
        List<Task> ready = new ArrayList<>();
        for (Task task : component) {
            if (indegree.get(task.core.id) == 0) {
                ready.add(task);
            }
        }

        Comparator<Task> comparator = Comparator
                .comparingInt((Task t) -> t.core.priority.value).reversed()
                .thenComparing(t -> t.core.title);

        while (!ready.isEmpty()) {
            ready.sort(comparator);
            Task current = ready.remove(0);
            ordered.add(current);

            for (String childId : outgoing.getOrDefault(current.core.id, Collections.emptyList())) {
                int next = indegree.get(childId) - 1;
                indegree.put(childId, next);
                if (next == 0) {
                    ready.add(componentMap.get(childId));
                }
            }
        }

        if (ordered.size() != component.size()) {
            component.sort(comparator);
            return component;
        }

        return ordered;
    }

    private ChainPlan simulateChainPlan(List<Task> orderedTasks, LocalDateTime start, LocalDateTime gapEnd) {
        if (orderedTasks.isEmpty()) {
            return null;
        }

        String chainId = orderedTasks.size() > 1 ? "chain-" + UUID.randomUUID() : null;
        List<PlannedSlot> planned = new ArrayList<>();
        Map<String, PlannedSlot> plannedByTask = new HashMap<>();
        LocalDateTime cursor = start;
        int totalScore = 0;

        for (int i = 0; i < orderedTasks.size(); i++) {
            Task task = orderedTasks.get(i);
            LocalDateTime candidateStart = cursor;

            if (task.prerequisites != null) {
                for (TaskPrerequisite prereq : task.prerequisites) {
                    PlannedSlot prereqPlanned = plannedByTask.get(prereq.prerequisiteId);
                    if (prereqPlanned != null) {
                        candidateStart = max(candidateStart, prereqPlanned.end.plusMinutes(prereq.minGapMinutes));
                        continue;
                    }
                    Task prereqTask = allTasksById.get(prereq.prerequisiteId);
                    if (prereqTask == null) {
                        continue;
                    }
                    TaskSlot existing = findScheduledSlotForDay(prereqTask, schedulingDay);
                    if (existing == null || existing.end == null) {
                        return null;
                    }
                    candidateStart = max(candidateStart,
                            schedulingDay.atTime(existing.end).plusMinutes(prereq.minGapMinutes));
                }
            }

            List<LocalDateTime> startCandidates = findPreferredStartCandidates(task, candidateStart, gapEnd);
            PlannedSlot bestSlot = null;
            for (LocalDateTime preferredStart : startCandidates) {
                if (hasUnmetPrerequisites(task, preferredStart, plannedByTask)) {
                    continue;
                }
                LocalDateTime slotEnd = preferredStart.plusMinutes(task.core.maxDuration);
                if (slotEnd.isAfter(gapEnd)) {
                    continue;
                }

                int score = scorer.score(task, preferredStart, slotEnd);
                if (score <= 0) {
                    continue;
                }

                PlannedSlot candidate = new PlannedSlot(task, preferredStart, slotEnd, score, i);
                if (bestSlot == null || candidate.score > bestSlot.score) {
                    bestSlot = candidate;
                }
            }

            if (bestSlot == null) {
                return null;
            }

            planned.add(bestSlot);
            plannedByTask.put(task.core.id, bestSlot);
            cursor = bestSlot.end;
            totalScore += bestSlot.score;
        }

        String label = "[" + planned.get(0).start.format(HMM) + "-" + planned.get(planned.size() - 1).end.format(HMM) + "]";
        return new ChainPlan(chainId, planned, totalScore, label);
    }

    private List<LocalDateTime> findPreferredStartCandidates(Task task, LocalDateTime baseStart, LocalDateTime gapEnd) {
        List<LocalDateTime> starts = new ArrayList<>();
        starts.add(baseStart);

        DayOfWeek today = schedulingDay.getDayOfWeek();
        for (TaskPrefSlot ps : task.prefSlots) {
            if (ps.days == null || !ps.days.contains(today)) continue;
            if (scorer.isPrefSlotConsumed(task.core.id, ps.id)) continue;
            LocalDateTime prefStart = schedulingDay.atTime(ps.start);
            if (prefStart.isBefore(baseStart) || !prefStart.isBefore(gapEnd)) continue;
            starts.add(prefStart);
        }

        starts.sort(LocalDateTime::compareTo);
        return starts;
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private void commitChainPlan(ChainPlan plan,
                                 TaskSlot parentSlot,
                                 int depth,
                                 List<Interval> occupied,
                                 String indent) {
        List<TaskSlot> committed = new ArrayList<>();
        for (PlannedSlot planned : plan.plannedSlots) {
            TaskSlot slot = createScheduledSlot(planned.task, planned.start, planned.score, parentSlot, plan.chainId, planned.chainOrder);
            LocalDateTime slotEnd = scheduleChildrenGapAware(planned.task, planned.start, slot, depth);
            slot.end = slotEnd.toLocalTime();
            committed.add(slot);
            insertSorted(occupied, new Interval(planned.start, slotEnd));
        }

        for (TaskSlot slot : committed) {
            Task task = allTasksById.get(slot.taskId);
            finalizeAssignment(task, slot);
        }

        if (plan.plannedSlots.size() == 1) {
            TaskSlot slot = committed.get(0);
            Task task = allTasksById.get(slot.taskId);
            log(indent + "  → " + task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "] score=" + slot.score);
        } else {
            TaskSlot first = committed.get(0);
            TaskSlot last = committed.get(committed.size() - 1);
            log(indent + "  → Chain committed (" + committed.size() + " Tasks) ["
                    + first.start.format(HMM) + "-" + last.end.format(HMM) + "] score=" + plan.score);
        }
    }

    private LocalDateTime scheduleChildrenGapAware(Task task, LocalDateTime parentStart, TaskSlot parentSlot, int depth) {
        LocalDateTime parentEnd = parentStart.plusMinutes(task.core.maxDuration);
        if (task.children.isEmpty()) {
            return parentEnd;
        }
        List<Interval> childOccupied = new ArrayList<>();
        assignGlobalBestFit(task.children, parentStart, parentEnd, parentSlot, depth + 1, childOccupied);
        return parentEnd;
    }

    private List<Interval> findGaps(List<Interval> occupied, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<Interval> gaps = new ArrayList<>();
        LocalDateTime cursor = windowStart;

        for (Interval interval : occupied) {
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

    private List<Interval> collectOccupiedIntervals(List<Task> tasks, LocalDate day, List<CalendarEvent> calendarEvents) {
        List<Interval> intervals = new ArrayList<>();
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.day.equals(day) && (slot.completed || slot.realStart != null)
                        && slot.start != null && slot.end != null) {
                    intervals.add(new Interval(
                            day.atTime(slot.start),
                            day.atTime(slot.end)));
                }
            }
        }
        for (CalendarEvent event : calendarEvents) {
            if (event.start() == null || event.end() == null || !event.end().isAfter(event.start())) {
                continue;
            }
            intervals.add(new Interval(day.atTime(event.start()), day.atTime(event.end())));
        }
        intervals.sort(Interval::compareTo);
        return intervals;
    }

    private void insertSorted(List<Interval> intervals, Interval newInterval) {
        int i = 0;
        while (i < intervals.size() && intervals.get(i).start.compareTo(newInterval.start) <= 0) {
            i++;
        }
        intervals.add(i, newInterval);
    }

    private TaskSlot createScheduledSlot(Task task,
                                        LocalDateTime cursor,
                                        int score,
                                        TaskSlot parentSlot,
                                        String chainId,
                                        int chainOrder) {
        TaskSlot slot = new TaskSlot();
        slot.taskId = task.core.id;
        slot.score = score;
        slot.day = cursor.toLocalDate();
        slot.start = cursor.toLocalTime();
        slot.scheduled = true;
        slot.parent = parentSlot != null ? parentSlot.id : null;
        slot.chainId = chainId;
        slot.chainOrder = chainOrder;
        return slot;
    }

    private void finalizeAssignment(Task task, TaskSlot slot) {
        task.slots.add(slot);
        scorer.onSlotAssigned(task, slot.start);
        scheduledInSession.add(task.core.id);
        newSlots++;
    }

    private String formatSlot(TaskSlot slot) {
        String start = slot.start != null ? slot.start.format(HMM) : "?";
        String end = slot.end != null ? slot.end.format(HMM) : "?";
        return start + "-" + end + "(" + slot.score + ")";
    }

    private boolean hasUnmetPrerequisites(Task task, LocalDateTime candidateStart) {
        return hasUnmetPrerequisites(task, candidateStart, Collections.emptyMap());
    }

    private boolean hasUnmetPrerequisites(Task task,
                                          LocalDateTime candidateStart,
                                          Map<String, PlannedSlot> plannedByTask) {
        if (task.prerequisites == null || task.prerequisites.isEmpty()) return false;
        for (TaskPrerequisite prereq : task.prerequisites) {
            PlannedSlot plannedSlot = plannedByTask.get(prereq.prerequisiteId);
            if (plannedSlot != null) {
                LocalDateTime earliest = plannedSlot.end.plusMinutes(prereq.minGapMinutes);
                if (candidateStart.isBefore(earliest)) {
                    return true;
                }
                continue;
            }

            Task prereqTask = allTasksById.get(prereq.prerequisiteId);
            if (prereqTask == null) continue;

            TaskSlot prereqSlot = findScheduledSlotForDay(prereqTask, schedulingDay);
            if (prereqSlot == null) {
                return true;
            }

            if (prereqSlot.end != null) {
                LocalDateTime earliestStart = schedulingDay.atTime(prereqSlot.end)
                        .plusMinutes(prereq.minGapMinutes);
                if (candidateStart.isBefore(earliestStart)) {
                    return true;
                }
            }
        }
        return false;
    }

    private TaskSlot findScheduledSlotForDay(Task task, LocalDate day) {
        TaskSlot latest = null;
        for (TaskSlot slot : task.slots) {
            if (!slot.day.equals(day) || (!slot.completed && !slot.scheduled)) {
                continue;
            }
            if (latest == null) {
                latest = slot;
                continue;
            }
            if (slot.end != null && latest.end != null && slot.end.isAfter(latest.end)) {
                latest = slot;
            }
        }
        return latest;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
