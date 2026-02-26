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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Internal scheduler that assigns tasks to time slots within a given window.
 */
public class DefaultTaskSlotGenerator {


    private static class CandidateSelection {
        private final Task task;
        private final int grossScore;
        private final int netScore;
        private final LocalDateTime startTime;
        private final List<TaskSlot> displacedSlots;
        private final String scoreLog;

        private CandidateSelection(Task task,
                                   int grossScore,
                                   int netScore,
                                   LocalDateTime startTime,
                                   List<TaskSlot> displacedSlots,
                                   String scoreLog) {
            this.task = task;
            this.grossScore = grossScore;
            this.netScore = netScore;
            this.startTime = startTime;
            this.displacedSlots = displacedSlots;
            this.scoreLog = scoreLog;
        }
    }

    private static class CandidateOption {
        private final Task task;
        private final LocalDateTime start;
        private final int grossScore;
        private final int netScore;
        private final List<TaskSlot> displacedSlots;

        private CandidateOption(Task task,
                                LocalDateTime start,
                                int grossScore,
                                int netScore,
                                List<TaskSlot> displacedSlots) {
            this.task = task;
            this.start = start;
            this.grossScore = grossScore;
            this.netScore = netScore;
            this.displacedSlots = displacedSlots;
        }
    }

    private static class BestCandidateTracker {
        private CandidateOption bestOption;

        private void update(CandidateOption option) {
            if (option == null || option.netScore <= 0) {
                return;
            }
            if (bestOption == null || option.netScore > bestOption.netScore) {
                bestOption = option;
            }
        }

        private CandidateSelection toSelection(String scoreLog) {
            if (bestOption == null) {
                return new CandidateSelection(null, 0, 0, null, List.of(), scoreLog);
            }
            return new CandidateSelection(
                    bestOption.task,
                    bestOption.grossScore,
                    bestOption.netScore,
                    bestOption.start,
                    bestOption.displacedSlots,
                    scoreLog
            );
        }
    }

    private static class ScoreLogBuilder {
        private final StringBuilder scores = new StringBuilder();

        private void appendTaskPrerequisiteBlocked(Task task) {
            appendSeparatorIfNeeded();
            scores.append(task.core.title).append(": 0 (Voraussetzung)");
        }

        private void appendTaskScore(Task task, int taskBestScore, List<String> prefDetails) {
            appendSeparatorIfNeeded();
            scores.append(task.core.title).append(": ").append(taskBestScore);
            for (String detail : prefDetails) {
                scores.append(detail);
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

    private void assignGlobalBestFit(List<Task> tasks, LocalDateTime windowStart, LocalDateTime windowEnd,
                                     TaskSlot parentSlot, int depth, List<Interval> occupied) {
        String indent = "  ".repeat(depth);

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

            List<TaskSlot> movableSlots = collectMovableDaySlots(windowStart, windowEnd);
            CandidateSelection selection = evaluateAllCandidates(tasks, gaps, movableSlots, windowEnd);
            log(indent + "  " + selection.scoreLog);

            if (selection.task == null || selection.netScore <= 0) {
                log(indent + "  → (keine Task mit positivem Netto-Gewinn, Abbruch)");
                break;
            }

            for (TaskSlot displaced : selection.displacedSlots) {
                Task owner = allTasksById.get(displaced.taskId);
                if (owner != null) {
                    owner.slots.remove(displaced);
                }
            }

            TaskSlot slot = createScheduledSlot(selection.task, selection.startTime, selection.grossScore, parentSlot);
            slot.chainId = selection.displacedSlots.isEmpty() ? slot.id : selection.displacedSlots.get(0).id;
            LocalDateTime slotEnd = scheduleChildrenGapAware(selection.task, selection.startTime, slot, depth);
            slot.end = slotEnd.toLocalTime();
            finalizeAssignment(selection.task, slot, selection.grossScore);

            log(indent + "  → " + selection.task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM)
                    + "] gross=" + selection.grossScore + ", net=" + selection.netScore
                    + (selection.displacedSlots.isEmpty() ? "" : ", displaced=" + selection.displacedSlots.size()));

            insertSorted(occupied, new Interval(selection.startTime, slotEnd));
        }
    }

    private CandidateSelection evaluateAllCandidates(List<Task> tasks,
                                                     List<Interval> gaps,
                                                     List<TaskSlot> movableSlots,
                                                     LocalDateTime windowEnd) {
        BestCandidateTracker tracker = new BestCandidateTracker();
        ScoreLogBuilder scoreLogBuilder = new ScoreLogBuilder();
        DayOfWeek today = schedulingDay.getDayOfWeek();

        for (Task task : tasks) {
            if (hasUnmetPrerequisites(task, windowEnd)) {
                scoreLogBuilder.appendTaskPrerequisiteBlocked(task);
                continue;
            }

            int taskBestNetScore = 0;
            List<String> prefDetails = new ArrayList<>();

            for (Interval gap : gaps) {
                CandidateOption gapOption = scoreCandidate(task, gap.start, windowEnd, movableSlots);
                if (gapOption.netScore > taskBestNetScore) {
                    taskBestNetScore = gapOption.netScore;
                }
                tracker.update(gapOption);

                for (TaskPrefSlot ps : task.prefSlots) {
                    if (ps.days == null || !ps.days.contains(today)) continue;
                    if (scorer.isPrefSlotConsumed(task.core.id, ps.id)) continue;
                    LocalDateTime prefStart = schedulingDay.atTime(ps.start);
                    if (!prefStart.isAfter(gap.start) || !prefStart.isBefore(gap.end)) continue;

                    CandidateOption prefOption = scoreCandidate(task, prefStart, windowEnd, movableSlots);
                    if (prefOption.netScore > taskBestNetScore) {
                        taskBestNetScore = prefOption.netScore;
                    }

                    CandidateOption previousBest = tracker.bestOption;
                    tracker.update(prefOption);
                    if (tracker.bestOption != null && tracker.bestOption != previousBest
                            && tracker.bestOption.task == task
                            && tracker.bestOption.start.equals(prefStart)) {
                        prefDetails.add("@" + ps.start.format(HMM) + "=" + prefOption.netScore);
                    }
                }
            }

            for (TaskSlot movable : movableSlots) {
                LocalDateTime displacedStart = schedulingDay.atTime(movable.start);
                CandidateOption displacedOption = scoreCandidate(task, displacedStart, windowEnd, movableSlots);
                if (displacedOption.netScore > taskBestNetScore) {
                    taskBestNetScore = displacedOption.netScore;
                }
                tracker.update(displacedOption);
            }

            scoreLogBuilder.appendTaskScore(task, taskBestNetScore, prefDetails);
        }

        return tracker.toSelection(scoreLogBuilder.build());
    }

    private CandidateOption scoreCandidate(Task task,
                                           LocalDateTime candidateStart,
                                           LocalDateTime windowEnd,
                                           List<TaskSlot> movableSlots) {
        if (hasUnmetPrerequisites(task, candidateStart)) {
            return new CandidateOption(task, candidateStart, 0, 0, List.of());
        }

        int candidateScore = scorer.score(task, candidateStart, windowEnd);
        if (candidateScore <= 0) {
            return new CandidateOption(task, candidateStart, 0, 0, List.of());
        }

        LocalDateTime candidateEnd = candidateStart.plusMinutes(task.core.maxDuration);
        List<TaskSlot> displaced = new ArrayList<>();
        int displacedScore = 0;
        for (TaskSlot slot : movableSlots) {
            if (slot.start == null || slot.end == null) {
                continue;
            }
            LocalDateTime slotStart = schedulingDay.atTime(slot.start);
            LocalDateTime slotEnd = schedulingDay.atTime(slot.end);
            if (candidateStart.isBefore(slotEnd) && candidateEnd.isAfter(slotStart)) {
                displaced.add(slot);
                displacedScore += displacementScore(slot);
            }
        }

        int netScore = candidateScore - displacedScore;
        if (!displaced.isEmpty() && netScore <= 0) {
            return new CandidateOption(task, candidateStart, candidateScore, 0, displaced);
        }
        return new CandidateOption(task, candidateStart, candidateScore, netScore, displaced);
    }

    private int displacementScore(TaskSlot slot) {
        if (slot.comparisonScore > 0) {
            return slot.comparisonScore;
        }
        return Math.max(0, slot.score);
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

    private List<TaskSlot> collectMovableDaySlots(LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<TaskSlot> movable = new ArrayList<>();
        for (Task task : allTasksById.values()) {
            for (TaskSlot slot : task.slots) {
                if (slot.day == null || !slot.day.equals(schedulingDay)) {
                    continue;
                }
                if (!slot.scheduled || slot.completed || slot.realStart != null) {
                    continue;
                }
                if (slot.start == null || slot.end == null) {
                    continue;
                }

                LocalDateTime slotStart = schedulingDay.atTime(slot.start);
                LocalDateTime slotEnd = schedulingDay.atTime(slot.end);
                if (!slotEnd.isAfter(windowStart) || !slotStart.isBefore(windowEnd)) {
                    continue;
                }
                movable.add(slot);
            }
        }
        return movable;
    }

    private void insertSorted(List<Interval> intervals, Interval newInterval) {
        int i = 0;
        while (i < intervals.size() && intervals.get(i).start.compareTo(newInterval.start) <= 0) {
            i++;
        }
        intervals.add(i, newInterval);
    }

    private TaskSlot createScheduledSlot(Task task, LocalDateTime cursor, int score, TaskSlot parentSlot) {
        TaskSlot slot = new TaskSlot();
        slot.taskId = task.core.id;
        slot.score = score;
        slot.comparisonScore = score;
        slot.day = cursor.toLocalDate();
        slot.start = cursor.toLocalTime();
        slot.scheduled = true;
        slot.parent = parentSlot != null ? parentSlot.id : null;
        return slot;
    }

    private void finalizeAssignment(Task task, TaskSlot slot, int score) {
        slot.score = score;
        slot.comparisonScore = score;
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
        if (task.prerequisites == null || task.prerequisites.isEmpty()) return false;
        for (TaskPrerequisite prereq : task.prerequisites) {
            Task prereqTask = allTasksById.get(prereq.prerequisiteId);
            if (prereqTask == null) continue;

            TaskSlot prereqSlot = findScheduledSlotForDay(prereqTask, schedulingDay);
            if (prereqSlot == null) {
                return true;
            }

            if (prereq.minGapMinutes > 0 && prereqSlot.end != null) {
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
