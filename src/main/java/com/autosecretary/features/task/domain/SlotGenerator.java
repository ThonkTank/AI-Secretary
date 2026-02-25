package com.autosecretary.features.task.domain;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

import com.autosecretary.features.task.data.*;

/**
 * Core scheduling algorithm that assigns tasks to time slots within a {@link TimeWindow}.
 * <p>
 * Uses gap-aware global best-fit placement: each iteration evaluates every task at every
 * available position (gap starts + preferred times within gaps), picks the (task, startTime)
 * pair with the highest composite score, places it, and repeats. This ensures tasks land at
 * their optimal times rather than being greedily placed at the earliest available slot.
 * </p>
 * <p>
 * Additional rules: tasks with unmet {@link TaskPrerequisite}s are skipped, and child tasks are
 * scheduled inside their parent's time block via recursive descent. Scoring is delegated to
 * {@link TaskScorer}.
 * </p>
 */
public class SlotGenerator {
    /**
     * Holds the result of candidate evaluation: the winning task, its score, the chosen start
     * time, and a log string summarizing all evaluated scores.
     */
    private static class CandidateSelection {
        private final Task task;
        private final int score;
        private final LocalDateTime startTime;
        private final String scoreLog;

        private CandidateSelection(Task task, int score, LocalDateTime startTime, String scoreLog) {
            this.task = task;
            this.score = score;
            this.startTime = startTime;
            this.scoreLog = scoreLog;
        }
    }

    /** Half-open time interval [start, end) representing an occupied block in the schedule. */
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
    private int newSlots;
    private Set<String> scheduledInSession;
    private Map<String, Task> allTasksById;
    private final TaskScorer scorer;
    private LocalDate schedulingDay;

    public SlotGenerator(TaskScorer scorer) {
        this(scorer, null);
    }

    public SlotGenerator(TaskScorer scorer, Consumer<String> logger) {
        this.scorer = scorer;
        this.logger = logger;
    }

    private static final DateTimeFormatter HMM = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Generates scheduled time slots for the given tasks within {@code window}.
     * Single-day convenience method that delegates to {@link #generateSlotsForDay} with empty state.
     *
     * @param tasks  flat list of all tasks (will be tree-built and re-flattened internally)
     * @param window the scheduling time boundaries
     * @return all tasks with slots assigned
     */
    public List<Task> generateSlots(List<Task> tasks, TimeWindow window) {
        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);
        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);
        generateSlotsForDay(allTasks, window, new MultiDayState());
        return allTasks;
    }

    /**
     * Generates scheduled time slots for one day, aware of cross-day scheduling state.
     * <p>
     * Builds the task tree, runs scorer maintenance (with day and multi-day state) on every task,
     * pre-registers preserved slots (completed/in-progress), then fills the window using
     * gap-aware global best-fit assignment.
     * </p>
     *
     * @param tasks  flat list of all tasks (already flattened by caller)
     * @param window the scheduling time boundaries for a single day
     * @param state  cross-day state tracking scheduled tasks across the week
     */
    public void generateSlotsForDay(List<Task> tasks, TimeWindow window, MultiDayState state) {
        schedulingDay = window.start().toLocalDate();
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

        // Pre-register preserved slots (completed/in-progress) for this day
        for (Task t : allTasks) {
            for (TaskSlot slot : t.slots) {
                if (slot.day.equals(schedulingDay) && (slot.completed || slot.realStart != null)) {
                    scheduledInSession.add(t.core.id);
                    scorer.onSlotAssigned(t);
                }
            }
        }

        long windowMin = ChronoUnit.MINUTES.between(window.start(), window.end());
        log("=== Generierung " + schedulingDay + " === Fenster " + window.start().format(HMM) + "-" + window.end().format(HMM) + " (" + windowMin + "min), " + taskTree.size() + " root tasks");

        List<Interval> occupied = collectOccupiedIntervals(allTasks, schedulingDay);
        assignGlobalBestFit(taskTree, window.start(), window.end(), null, 0, occupied);

        log("=== Zusammenfassung " + schedulingDay + " ===");
        int totalDaySlots = 0;
        for (Task t : allTasks) {
            List<TaskSlot> daySlots = new ArrayList<>();
            for (TaskSlot s : t.slots) {
                if (s.day.equals(schedulingDay) && s.scheduled) daySlots.add(s);
            }
            if (!daySlots.isEmpty()) {
                totalDaySlots += daySlots.size();
                log("  " + t.core.title + ": " + daySlots.size() + " slots [" + formatSlotsSummary(daySlots) + "]");
            } else {
                log("  " + t.core.title + ": unscheduled");
            }
        }
        log("Gesamt: " + totalDaySlots + " slots (neu: " + newSlots + ")");
    }

    // ========================================================================
    // Gap-aware global best-fit scheduling
    // ========================================================================

    /**
     * Main scheduling loop: iteratively finds the globally best (task, startTime) pair
     * across all available gaps and places it. Repeats until no task qualifies.
     */
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

            CandidateSelection selection = evaluateAllCandidates(tasks, gaps, windowEnd);
            log(indent + "  " + selection.scoreLog);

            if (selection.task == null || selection.score <= 0) {
                log(indent + "  → (keine Task qualifiziert, Abbruch)");
                break;
            }

            TaskSlot slot = createScheduledSlot(selection.task, selection.startTime, selection.score, parentSlot);
            LocalDateTime slotEnd = scheduleChildrenGapAware(selection.task, selection.startTime, slot, depth);
            slot.end = slotEnd.toLocalTime();
            finalizeAssignment(selection.task, slot, selection.score);

            log(indent + "  → " + selection.task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "] score=" + selection.score);

            insertSorted(occupied, new Interval(selection.startTime, slotEnd));
        }
    }

    /**
     * Evaluates all tasks at all available positions (gap starts + preferred times within gaps).
     * Returns the (task, startTime) pair with the highest score.
     */
    private CandidateSelection evaluateAllCandidates(List<Task> tasks, List<Interval> gaps, LocalDateTime windowEnd) {
        Task bestTask = null;
        int bestScore = 0;
        LocalDateTime bestStart = null;
        StringBuilder scores = new StringBuilder();
        DayOfWeek today = schedulingDay.getDayOfWeek();

        for (Task task : tasks) {
            appendScoreSeparator(scores);
            if (hasUnmetPrerequisites(task)) {
                scores.append(formatPrerequisiteBlockedScore(task));
                continue;
            }

            int taskBestScore = 0;

            for (Interval gap : gaps) {
                LocalDateTime gapEnd = gap.end.isBefore(windowEnd) ? gap.end : windowEnd;

                // Evaluate at gap start
                int gapScore = scorer.score(task, gap.start, gapEnd);
                if (gapScore > taskBestScore) {
                    taskBestScore = gapScore;
                }
                if (gapScore > bestScore) {
                    bestScore = gapScore;
                    bestTask = task;
                    bestStart = gap.start;
                }

                // Evaluate at each preferred time within this gap
                for (TaskPrefSlot ps : task.prefSlots) {
                    if (ps.days == null || !ps.days.contains(today)) continue;
                    LocalDateTime prefStart = schedulingDay.atTime(ps.start);
                    if (!prefStart.isAfter(gap.start) || !prefStart.isBefore(gap.end)) continue;
                    int prefScore = scorer.score(task, prefStart, gapEnd);
                    if (prefScore > taskBestScore) {
                        taskBestScore = prefScore;
                    }
                    if (prefScore > bestScore) {
                        bestScore = prefScore;
                        bestTask = task;
                        bestStart = prefStart;
                        scores.append("@" + ps.start.format(HMM) + "=" + prefScore);
                    }
                }
            }

            scores.append(formatScore(task, taskBestScore));
        }

        return new CandidateSelection(bestTask, bestScore, bestStart, scores.toString());
    }

    /**
     * Schedules children inside their parent's time block using the same global-best-fit approach.
     */
    private LocalDateTime scheduleChildrenGapAware(Task task, LocalDateTime parentStart, TaskSlot parentSlot, int depth) {
        LocalDateTime parentEnd = parentStart.plusMinutes(task.core.maxDuration);
        if (task.children.isEmpty()) {
            return parentEnd;
        }
        List<Interval> childOccupied = new ArrayList<>();
        assignGlobalBestFit(task.children, parentStart, parentEnd, parentSlot, depth + 1, childOccupied);
        return parentEnd;
    }

    // ========================================================================
    // Gap tracking
    // ========================================================================

    /** Computes free gaps between occupied intervals within the given window. */
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

    /** Scans all tasks for preserved (completed/in-progress) slots to seed the initial occupied list. */
    private List<Interval> collectOccupiedIntervals(List<Task> tasks, LocalDate day) {
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
        intervals.sort(Interval::compareTo);
        return intervals;
    }

    /** Inserts a new interval into the sorted list at the correct position. */
    private void insertSorted(List<Interval> intervals, Interval newInterval) {
        int i = 0;
        while (i < intervals.size() && intervals.get(i).start.compareTo(newInterval.start) <= 0) {
            i++;
        }
        intervals.add(i, newInterval);
    }

    // ========================================================================
    // Slot creation and finalization (unchanged)
    // ========================================================================

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
        scorer.onSlotAssigned(task);
        scheduledInSession.add(task.core.id);
        newSlots++;
    }

    // ========================================================================
    // Formatting and logging helpers (unchanged)
    // ========================================================================

    private String formatSlotsSummary(List<TaskSlot> slots) {
        StringBuilder sb = new StringBuilder();
        for (TaskSlot slot : slots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(formatSlot(slot));
        }
        return sb.toString();
    }

    private String formatSlot(TaskSlot slot) {
        String start = slot.start != null ? slot.start.format(HMM) : "?";
        String end = slot.end != null ? slot.end.format(HMM) : "?";
        return start + "-" + end + "(" + slot.score + ")";
    }

    private void appendScoreSeparator(StringBuilder scores) {
        if (scores.length() > 0) {
            scores.append("  |  ");
        }
    }

    private String formatPrerequisiteBlockedScore(Task task) {
        return task.core.title + ": 0 (Voraussetzung)";
    }

    private String formatScore(Task task, int score) {
        return task.core.title + ": " + score;
    }

    /**
     * Returns {@code true} if any of the task's {@link TaskPrerequisite}s are unsatisfied.
     * A prerequisite is satisfied if its referenced task was scheduled in the current generation
     * session or already has a scheduled/completed slot for today.
     */
    private boolean hasUnmetPrerequisites(Task task) {
        if (task.prerequisites == null || task.prerequisites.isEmpty()) return false;
        for (TaskPrerequisite prereq : task.prerequisites) {
            if (scheduledInSession.contains(prereq.prerequisiteId)) continue;
            Task prereqTask = allTasksById.get(prereq.prerequisiteId);
            if (prereqTask == null) continue;
            boolean satisfied = false;
            for (TaskSlot slot : prereqTask.slots) {
                if (slot.day.equals(schedulingDay) && (slot.completed || slot.scheduled)) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) return true;
        }
        return false;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
