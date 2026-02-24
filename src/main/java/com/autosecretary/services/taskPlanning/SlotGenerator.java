package com.autosecretary.services.taskPlanning;

import android.util.Log;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

import com.autosecretary.database.*;
import com.autosecretary.database.task.*;

public class SlotGenerator {
    private TaskDAO taskDao;
    private LocalDateTime prefStart;
    private LocalDateTime prefEnd;
    private int newSlots;
    private Set<String> scheduledInSession;
    private Map<String, Task> allTasksById;
    private TaskScorer scorer;

    public SlotGenerator(TaskDAO dao, LocalDateTime start, LocalDateTime end, TaskScorer scorer) {
        this.taskDao = dao;
        this.prefStart = start;
        this.prefEnd = end;
        this.scorer = scorer;
    }

    private static final String TAG = "SlotGen";
    private static final DateTimeFormatter HMM = DateTimeFormatter.ofPattern("HH:mm");

    public void generateSlots() {
        // Task baum bauen
        newSlots = 0;
        scorer.reset();
        List<Task> tasks = taskDao.readAll();
        List<Task> taskTree = TaskTreeOperations.buildTree(tasks);

        List<Task> allTasks = TaskTreeOperations.flatten(taskTree);
        scheduledInSession = new HashSet<>();
        allTasksById = new HashMap<>();
        for (Task t : allTasks) {
            allTasksById.put(t.core.id, t);
            scorer.maintenance(t);
        }

        long windowMin = ChronoUnit.MINUTES.between(prefStart, prefEnd);
        Log.d(TAG, "=== Generierung Start === Fenster " + prefStart.format(HMM) + "-" + prefEnd.format(HMM) + " (" + windowMin + "min), " + taskTree.size() + " root tasks");

        assignSlot(taskTree, prefStart, prefEnd, null, 0);

        // Zusammenfassung
        Log.d(TAG, "=== Zusammenfassung ===");
        for (Task t : allTasks) {
            int slotCount = t.slots.size();
            if (slotCount > 0) {
                StringBuilder sb = new StringBuilder();
                for (TaskSlot s : t.slots) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s.start != null ? s.start.format(HMM) : "?");
                    sb.append("-");
                    sb.append(s.end != null ? s.end.format(HMM) : "?");
                    sb.append("(").append(s.score).append(")");
                }
                Log.d(TAG, "  " + t.core.title + ": " + slotCount + " slots [" + sb + "]");
            } else {
                Log.d(TAG, "  " + t.core.title + ": unscheduled");
            }
        }
        Log.d(TAG, "Gesamt: " + newSlots + " slots");

        taskDao.writeList(allTasks);
    }

    private LocalDateTime assignSlot(List<Task> tasks, LocalDateTime cursor, LocalDateTime end, TaskSlot parentSlot, int depth) {
        String indent = "  ".repeat(depth);

        while (cursor.isBefore(end)) {
            long remaining = ChronoUnit.MINUTES.between(cursor, end);
            Log.d(TAG, indent + "--- Cursor " + cursor.format(HMM) + " [depth=" + depth + "], " + remaining + "min übrig ---");

            Task bestTask = null;
            int bestScore = 0;
            StringBuilder scores = new StringBuilder();
            for (Task task : tasks) {
                if (scores.length() > 0) scores.append("  |  ");
                if (hasUnmetPrerequisites(task)) {
                    scores.append(task.core.title).append(": 0 (Voraussetzung)");
                    continue;
                }
                int score = scorer.score(task, cursor, end);
                scores.append(task.core.title).append(": ").append(score);
                if (score > bestScore) {
                    bestScore = score;
                    bestTask = task;
                }
            }
            Log.d(TAG, indent + "  " + scores);

            if (bestScore == 0) {
                Log.d(TAG, indent + "  → (keine Task qualifiziert, Abbruch)");
                break;
            }

            TaskSlot slot = new TaskSlot();
            slot.taskId = bestTask.core.id;
            slot.score = bestScore;
            slot.day = cursor.toLocalDate();
            slot.start = cursor.toLocalTime();
            slot.scheduled = true;
            slot.parent = parentSlot != null ? parentSlot.id : null;

            LocalDateTime slotEnd = cursor.plusMinutes(bestTask.core.maxDuration);
            LocalDateTime childEnd = assignSlot(bestTask.children, cursor, slotEnd, slot, depth + 1);
            slotEnd = childEnd.isAfter(cursor) ? childEnd : slotEnd;
            slot.end = slotEnd.toLocalTime();
            bestTask.slots.add(slot);
            scorer.onSlotAssigned(bestTask);
            scheduledInSession.add(bestTask.core.id);
            newSlots++;

            Log.d(TAG, indent + "  → " + bestTask.core.title + " [" + slot.start.format(HMM) + "-" + slot.end.format(HMM) + "] score=" + bestScore);

            cursor = slotEnd;
        }

        return cursor;
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
}