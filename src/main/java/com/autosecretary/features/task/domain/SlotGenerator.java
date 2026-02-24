package com.autosecretary.features.task.domain;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

import com.autosecretary.features.task.data.*;

public class SlotGenerator {
    private static class CandidateSelection {
        private final Task task;
        private final int score;
        private final String scoreLog;

        private CandidateSelection(Task task, int score, String scoreLog) {
            this.task = task;
            this.score = score;
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

        assignSlot(taskTree, window.start(), window.end(), null, 0);

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

    private LocalDateTime assignSlot(List<Task> tasks, LocalDateTime cursor, LocalDateTime end, TaskSlot parentSlot, int depth) {
        String indent = "  ".repeat(depth);

        while (cursor.isBefore(end)) {
            long remaining = ChronoUnit.MINUTES.between(cursor, end);
            log(indent + "--- Cursor " + cursor.format(HMM) + " [depth=" + depth + "], " + remaining + "min übrig ---");

            CandidateSelection selection = selectBestTask(tasks, cursor, end);
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

    private CandidateSelection selectBestTask(List<Task> tasks, LocalDateTime cursor, LocalDateTime end) {
        Task bestTask = null;
        int bestScore = 0;
        StringBuilder scores = new StringBuilder();

        for (Task task : tasks) {
            appendScoreSeparator(scores);
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

        return new CandidateSelection(bestTask, bestScore, scores.toString());
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
        LocalDateTime childEnd = assignSlot(task.children, cursor, slotEnd, slot, depth + 1);
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
