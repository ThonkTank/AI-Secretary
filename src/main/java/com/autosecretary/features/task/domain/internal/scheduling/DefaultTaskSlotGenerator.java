package com.autosecretary.features.task.domain.internal.scheduling;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.TaskCalendarEvent;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.TaskPlanningState;
import com.autosecretary.features.task.domain.TaskSlotGenerator;
import com.autosecretary.features.task.domain.TaskTreeOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
        final Task task;
        final TaskSlot slot;
        final boolean displaceable;

        OccupiedInterval(LocalDateTime start, LocalDateTime end, Task task, TaskSlot slot, boolean displaceable) {
            super(start, end);
            this.task = task;
            this.slot = slot;
            this.displaceable = displaceable;
        }
    }

    private static class FixedInterval extends OccupiedInterval {
        FixedInterval(LocalDateTime start, LocalDateTime end) {
            super(start, end, null, null, false);
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
        final Set<TaskSlot> toDisplace;
        final int gainScore;
        final int lossScore;
        final int netScore;

        ChainPlacement(List<ChainNode> chain,
                       List<LocalDateTime> starts,
                       Set<TaskSlot> toDisplace,
                       int gainScore,
                       int lossScore) {
            this.chain = chain;
            this.starts = starts;
            this.toDisplace = toDisplace;
            this.gainScore = gainScore;
            this.lossScore = lossScore;
            this.netScore = gainScore - lossScore;
        }
    }

    private final Consumer<String> logger;
    private final SchedulingWindowProvider schedulingWindowProvider;
    private final CalendarBlockedIntervalProvider calendarBlockedIntervalProvider;
    private final TaskScorer scorer;
    private final List<SchedulingConflict> lastConflicts = new ArrayList<>();

    private int newSlots;
    private Set<String> scheduledInSession;
    private Map<String, Task> allTasksById;
    private LocalDate schedulingDay;

    private static final DateTimeFormatter HMM = DateTimeFormatter.ofPattern("HH:mm");

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
        generateSlotsForDayInternal(tasks, window.start, window.end, state, new ArrayList<>());
    }

    public void generateSlotsForDay(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    TaskPlanningState state,
                                    List<TaskCalendarEvent> calendarEvents) {
        generateSlotsForDayInternal(tasks, windowStart, windowEnd, state, calendarEvents);
    }

    private void generateSlotsForDayInternal(List<Task> tasks,
                                             LocalDateTime windowStart,
                                             LocalDateTime windowEnd,
                                             TaskPlanningState state,
                                             List<TaskCalendarEvent> calendarEvents) {
        schedulingDay = windowStart.toLocalDate();
        newSlots = 0;
        scorer.reset();
        lastConflicts.clear();

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

        List<OccupiedInterval> occupied = collectOccupiedIntervals(allTasks, schedulingDay, calendarEvents);
        for (CalendarBlockedIntervalProvider.BlockedInterval blocked :
                calendarBlockedIntervalProvider.readBlockedIntervals(schedulingDay, windowStart, windowEnd)) {
            occupied.add(new FixedInterval(blocked.start, blocked.end));
        }
        occupied.sort(Interval::compareTo);

        scheduleFixedTasks(taskTree, windowStart, windowEnd, occupied);
        assignGlobalBestFit(taskTree, windowStart, windowEnd, occupied);

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
                    if (summary.length() > 0) summary.append(", ");
                    summary.append(formatSlot(slot));
                }
                log("  " + t.core.title + ": " + daySlots.size() + " slots [" + summary + "]");
            } else {
                log("  " + t.core.title + ": unscheduled");
            }
        }
        log("Gesamt: " + totalDaySlots + " slots (neu: " + newSlots + ")");
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

    private void assignGlobalBestFit(List<Task> tasks,
                                     LocalDateTime windowStart,
                                     LocalDateTime windowEnd,
                                     List<OccupiedInterval> occupied) {
        while (true) {
            List<LocalDateTime> startPoints = collectStartPoints(findGaps(occupied, windowStart, windowEnd), occupied);
            if (startPoints.isEmpty()) {
                break;
            }

            List<List<ChainNode>> chains = buildTaskChains(tasks);
            ChainPlacement best = null;
            for (List<ChainNode> chain : chains) {
                ChainPlacement chainBest = evaluateChainCandidates(chain, startPoints, windowEnd, occupied);
                if (chainBest != null && chainBest.netScore > 0 && (best == null || chainBest.netScore > best.netScore)) {
                    best = chainBest;
                }
            }

            if (best == null) {
                break;
            }

            applyPlacement(best, occupied);
        }
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
        Set<TaskSlot> toDisplace = new HashSet<>();
        int gain = 0;

        LocalDateTime cursor = firstStart;
        for (int i = 0; i < chain.size(); i++) {
            ChainNode node = chain.get(i);
            Task task = node.task;
            if (i > 0) {
                cursor = cursor.plusMinutes(node.minGapFromPrevious);
            }

            if (hasUnmetPrerequisites(task, cursor, starts, chain, i)) {
                return null;
            }

            LocalDateTime end = cursor.plusMinutes(task.core.maxDuration);
            if (!end.isAfter(cursor) || end.isAfter(windowEnd)) {
                return null;
            }

            Set<OccupiedInterval> overlaps = findOverlappingIntervals(occupied, cursor, end);
            for (OccupiedInterval overlap : overlaps) {
                if (!overlap.displaceable) {
                    return null;
                }
            }

            expandToFullChains(overlaps, occupied, toDisplace);
            int score = scorer.score(task, cursor, windowEnd.isBefore(end) ? windowEnd : end);
            if (score <= 0) {
                return null;
            }
            gain += score;
            starts.add(cursor);
            cursor = end;
        }

        gain += chain.size() * 10;
        int loss = 0;
        for (TaskSlot slot : toDisplace) {
            loss += Math.max(0, slot.score);
        }

        return new ChainPlacement(new ArrayList<>(chain), starts, toDisplace, gain, loss);
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

            TaskSlot prereqSlot = findScheduledSlotForDay(prereqTask, schedulingDay);
            LocalDateTime prereqEnd = prereqSlot != null && prereqSlot.end != null
                    ? schedulingDay.atTime(prereqSlot.end)
                    : null;

            if (prereqEnd == null) {
                for (int i = 0; i < currentIndex; i++) {
                    ChainNode n = chain.get(i);
                    if (!n.task.core.id.equals(prereq.prerequisiteId)) {
                        continue;
                    }
                    LocalDateTime start = chainStarts.get(i);
                    prereqEnd = start.plusMinutes(n.task.core.maxDuration);
                    break;
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

    private List<List<ChainNode>> buildTaskChains(List<Task> tasks) {
        Map<String, Task> byId = new HashMap<>();
        for (Task task : tasks) {
            byId.put(task.core.id, task);
        }

        Map<String, List<ChainNode>> outgoing = new HashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        for (Task task : tasks) {
            if (task.prerequisites == null) {
                continue;
            }
            for (TaskPrerequisite prerequisite : task.prerequisites) {
                if (!byId.containsKey(prerequisite.prerequisiteId)) {
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
            slot.end = start.plusMinutes(task.core.maxDuration).toLocalTime();
            slot.chainId = chainId;
            finalizeAssignment(task, slot, slot.score);
            occupied.add(new OccupiedInterval(start, start.plusMinutes(task.core.maxDuration), task, slot, true));
        }
        occupied.sort(Interval::compareTo);
    }

    private void removeDisplacedSlots(Set<TaskSlot> displaced, List<OccupiedInterval> occupied) {
        if (displaced.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (TaskSlot slot : displaced) {
            ids.add(slot.id);
            Task owner = allTasksById.get(slot.taskId);
            if (owner != null) {
                owner.slots.removeIf(existing -> existing.id.equals(slot.id));
            }
        }
        occupied.removeIf(interval -> interval.slot != null && ids.contains(interval.slot.id));
    }

    public List<SchedulingConflict> getLastConflicts() {
        return new ArrayList<>(lastConflicts);
    }

    private void scheduleFixedTasks(List<Task> tasks,
                                    LocalDateTime windowStart,
                                    LocalDateTime windowEnd,
                                    List<OccupiedInterval> occupied) {
        List<Task> fixedTasks = new ArrayList<>();
        collectFixedTasks(tasks, fixedTasks);
        fixedTasks.sort((a, b) -> {
            if (a.core.fixedStart == null && b.core.fixedStart == null) return 0;
            if (a.core.fixedStart == null) return 1;
            if (b.core.fixedStart == null) return -1;
            return a.core.fixedStart.compareTo(b.core.fixedStart);
        });

        for (Task task : fixedTasks) {
            if (task.core.fixedDate == null || !task.core.fixedDate.equals(schedulingDay) || task.core.fixedStart == null) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(task.core.fixedDate, task.core.fixedStart);
            LocalDateTime end = computeFixedEnd(task, start);
            if (end == null || !end.isAfter(start) || start.isBefore(windowStart) || end.isAfter(windowEnd)) {
                lastConflicts.add(new SchedulingConflict(task.core.id, task.core.title,
                        SchedulingConflict.Reason.DAY_BOUNDARY, start, end,
                        "Termin liegt außerhalb der Tagesgrenzen"));
                continue;
            }
            if (!findOverlappingIntervals(occupied, start, end).isEmpty()) {
                lastConflicts.add(new SchedulingConflict(task.core.id, task.core.title,
                        SchedulingConflict.Reason.FIXED_VS_FIXED, start, end,
                        "Termin überschneidet belegte Zeit"));
                continue;
            }
            TaskSlot slot = createScheduledSlot(task, start, Integer.MAX_VALUE / 2, null);
            slot.end = end.toLocalTime();
            finalizeAssignment(task, slot, Integer.MAX_VALUE / 2);
            occupied.add(new FixedInterval(start, end));
            occupied.sort(Interval::compareTo);
        }
    }

    private void collectFixedTasks(List<Task> tasks, List<Task> fixedTasks) {
        for (Task task : tasks) {
            if (task.core.schedulingType == TaskCore.SchedulingType.TERMIN) {
                fixedTasks.add(task);
            }
            if (task.children != null && !task.children.isEmpty()) {
                collectFixedTasks(task.children, fixedTasks);
            }
        }
    }

    private LocalDateTime computeFixedEnd(Task task, LocalDateTime start) {
        if (task.core.fixedEnd != null) {
            return LocalDateTime.of(start.toLocalDate(), task.core.fixedEnd);
        }
        int duration = task.core.fixedDuration != null ? task.core.fixedDuration : task.core.maxDuration;
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
            if (interval.displaceable) {
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
                                    Set<TaskSlot> expandedSlots) {
        ArrayDeque<OccupiedInterval> queue = new ArrayDeque<>(overlapping);
        Set<String> chainIds = new HashSet<>();

        while (!queue.isEmpty()) {
            OccupiedInterval interval = queue.poll();
            if (interval.slot == null) {
                continue;
            }
            expandedSlots.add(interval.slot);
            if (interval.slot.chainId != null && chainIds.add(interval.slot.chainId)) {
                for (OccupiedInterval candidate : occupied) {
                    if (candidate.slot != null && interval.slot.chainId.equals(candidate.slot.chainId)) {
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
                    intervals.add(new OccupiedInterval(
                            day.atTime(slot.start),
                            day.atTime(slot.end),
                            task,
                            slot,
                            !locked));
                }
            }
        }
        for (TaskCalendarEvent event : calendarEvents) {
            if (event.start() == null || event.end() == null || !event.end().isAfter(event.start())) {
                continue;
            }
            intervals.add(new FixedInterval(day.atTime(event.start()), day.atTime(event.end())));
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

    private void finalizeAssignment(Task task, TaskSlot slot, int score) {
        slot.score = score;
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

    private TaskSlot findScheduledSlotForDay(Task task, LocalDate day) {
        for (TaskSlot slot : task.slots) {
            if (slot.day.equals(day) && (slot.completed || slot.scheduled)) {
                return slot;
            }
        }
        return null;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
