package com.autosecretary.features.task.domain;

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
 * The algorithm uses cursor-based greedy assignment with preferred-time lookahead. For each
 * cursor position, it evaluates every task at both the current cursor and at each of the task's
 * preferred start times (from {@link TaskPrefSlot}). The (task, startTime) pair with the highest
 * composite score wins. When the winning task's start is after the cursor, the gap is filled
 * recursively using greedy-only selection (no lookahead), with the "anchored" task excluded to
 * prevent premature placement. Unfillable gaps remain as free time.
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

    private final Consumer<String> logger;
    private int newSlots;
    private Set<String> scheduledInSession;
    private Map<String, Task> allTasksById;
    private final TaskScorer scorer;

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
     * <p>
     * Builds the task tree, runs scorer maintenance on every task, then fills the window using
     * lookahead-aware assignment. Returns the flat list of all tasks with their newly assigned
     * {@link TaskSlot}s attached.
     * </p>
     *
     * @param tasks  flat list of all tasks (will be tree-built and re-flattened internally)
     * @param window the scheduling time boundaries
     * @return all tasks with slots assigned
     */
    public List<Task> generateSlots(List<Task> tasks, TimeWindow window) {
        newSlots = 0;
        scorer.reset();
        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);

        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);
        scheduledInSession = new HashSet<>();
        allTasksById = new HashMap<>();
        for (Task t : allTasks) {
            allTasksById.put(t.core.id, t);
            scorer.maintenance(t);
        }

        long windowMin = ChronoUnit.MINUTES.between(window.start(), window.end());
        log("=== Generierung Start === Fenster " + window.start().format(HMM) + "-" + window.end().format(HMM) + " (" + windowMin + "min), " + taskTree.size() + " root tasks");

        assignWithLookahead(taskTree, window.start(), window.end(), null, 0);

        log("=== Zusammenfassung ===");
        for (Task t : allTasks) {
            int slotCount = t.slots.size();
            if (slotCount > 0) {
                log("  " + t.core.title + ": " + slotCount + " slots [" + formatSlotsSummary(t.slots) + "]");
            } else {
                log("  " + t.core.title + ": unscheduled");
            }
        }
        log("Gesamt: " + newSlots + " slots");

        return allTasks;
    }

    private LocalDateTime assignWithLookahead(List<Task> tasks, LocalDateTime cursor, LocalDateTime end, TaskSlot parentSlot, int depth) {
        String indent = "  ".repeat(depth);

        while (cursor.isBefore(end)) {
            long remaining = ChronoUnit.MINUTES.between(cursor, end);
            log(indent + "--- Cursor " + cursor.format(HMM) + " [depth=" + depth + "], " + remaining + "min übrig ---");

            CandidateSelection selection = selectWithLookahead(tasks, cursor, end);
            log(indent + "  " + selection.scoreLog);

            if (selection.task == null || selection.score <= 0) {
                log(indent + "  → (keine Task qualifiziert, Abbruch)");
                break;
            }

            if (selection.startTime.isAfter(cursor)) {
                log(indent + "  → Lücke [" + cursor.format(HMM) + "-" + selection.startTime.format(HMM) + "] vor " + selection.task.core.title + " wird gefüllt");
                cursor = assignGreedy(tasks, cursor, selection.startTime, parentSlot, depth, selection.task.core.id);
                if (cursor.isBefore(selection.startTime)) {
                    long gapMin = ChronoUnit.MINUTES.between(cursor, selection.startTime);
                    log(indent + "  → Freie Lücke [" + cursor.format(HMM) + "-" + selection.startTime.format(HMM) + "] (" + gapMin + "min)");
                    cursor = selection.startTime;
                }
            }

            TaskSlot slot = createScheduledSlot(selection.task, cursor, selection.score, parentSlot);
            LocalDateTime slotEnd = scheduleChildren(selection.task, cursor, slot, depth);
            slot.end = slotEnd.toLocalTime();
            finalizeAssignment(selection.task, slot, selection.score);

            log(indent + "  → " + selection.task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "] score=" + selection.score);

            cursor = slotEnd;
        }

        return cursor;
    }

    private LocalDateTime assignGreedy(List<Task> tasks, LocalDateTime cursor, LocalDateTime end, TaskSlot parentSlot, int depth, String excludeId) {
        String indent = "  ".repeat(depth);

        while (cursor.isBefore(end)) {
            long remaining = ChronoUnit.MINUTES.between(cursor, end);
            log(indent + "--- Cursor " + cursor.format(HMM) + " [depth=" + depth + ", greedy], " + remaining + "min übrig ---");

            CandidateSelection selection = selectAtCursor(tasks, cursor, end, excludeId);
            log(indent + "  " + selection.scoreLog);

            if (selection.task == null || selection.score <= 0) {
                log(indent + "  → (keine Task qualifiziert, Abbruch)");
                break;
            }

            TaskSlot slot = createScheduledSlot(selection.task, cursor, selection.score, parentSlot);
            LocalDateTime slotEnd = scheduleChildren(selection.task, cursor, slot, depth);
            slot.end = slotEnd.toLocalTime();
            finalizeAssignment(selection.task, slot, selection.score);

            log(indent + "  → " + selection.task.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "] score=" + selection.score);

            cursor = slotEnd;
        }

        return cursor;
    }

    private CandidateSelection selectWithLookahead(List<Task> tasks, LocalDateTime cursor, LocalDateTime end) {
        Task bestTask = null;
        int bestScore = 0;
        LocalDateTime bestStart = cursor;
        StringBuilder scores = new StringBuilder();
        DayOfWeek today = cursor.toLocalDate().getDayOfWeek();

        for (Task task : tasks) {
            appendScoreSeparator(scores);
            if (hasUnmetPrerequisites(task)) {
                scores.append(formatPrerequisiteBlockedScore(task));
                continue;
            }

            int cursorScore = scorer.score(task, cursor, end);
            scores.append(formatScore(task, cursorScore));
            if (cursorScore > bestScore) {
                bestScore = cursorScore;
                bestTask = task;
                bestStart = cursor;
            }

            for (TaskPrefSlot ps : task.prefSlots) {
                if (ps.days == null || !ps.days.contains(today)) continue;
                LocalDateTime prefStart = cursor.toLocalDate().atTime(ps.start);
                if (!prefStart.isAfter(cursor) || !prefStart.isBefore(end)) continue;
                int prefScore = scorer.score(task, prefStart, end);
                if (prefScore > bestScore) {
                    bestScore = prefScore;
                    bestTask = task;
                    bestStart = prefStart;
                    scores.append("@" + ps.start.format(HMM) + "=" + prefScore);
                }
            }
        }

        return new CandidateSelection(bestTask, bestScore, bestStart, scores.toString());
    }

    private CandidateSelection selectAtCursor(List<Task> tasks, LocalDateTime cursor, LocalDateTime end, String excludeId) {
        Task bestTask = null;
        int bestScore = 0;
        StringBuilder scores = new StringBuilder();

        for (Task task : tasks) {
            appendScoreSeparator(scores);
            if (excludeId != null && excludeId.equals(task.core.id)) {
                scores.append(task.core.title + ": -- (excluded)");
                continue;
            }
            if (hasUnmetPrerequisites(task)) {
                scores.append(formatPrerequisiteBlockedScore(task));
                continue;
            }

            int score = scorer.score(task, cursor, end);
            scores.append(formatScore(task, score));
            if (score > bestScore) {
                bestScore = score;
                bestTask = task;
            }
        }

        return new CandidateSelection(bestTask, bestScore, cursor, scores.toString());
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
        scorer.onSlotAssigned(task);
        scheduledInSession.add(task.core.id);
        newSlots++;
    }

    private LocalDateTime scheduleChildren(Task task, LocalDateTime cursor, TaskSlot slot, int depth) {
        LocalDateTime slotEnd = cursor.plusMinutes(task.core.maxDuration);
        LocalDateTime childEnd = assignWithLookahead(task.children, cursor, slotEnd, slot, depth + 1);
        return childEnd.isAfter(cursor) ? childEnd : slotEnd;
    }

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
        LocalDate today = LocalDate.now();
        for (TaskPrerequisite prereq : task.prerequisites) {
            if (scheduledInSession.contains(prereq.prerequisiteId)) continue;
            Task prereqTask = allTasksById.get(prereq.prerequisiteId);
            if (prereqTask == null) continue;
            boolean satisfied = false;
            for (TaskSlot slot : prereqTask.slots) {
                if (slot.day.equals(today) && (slot.completed || slot.scheduled)) {
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
