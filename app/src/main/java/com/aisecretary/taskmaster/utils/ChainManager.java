package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChainManager - Manages task chains and sequences
 *
 * Handles task dependencies and sequential workflows (A → B → C → A)
 * Phase 6: Verkettete Tasks
 */
public class ChainManager {

    /**
     * Data class for chain information
     */
    public static class ChainInfo {
        public String chainId;
        public List<TaskEntity> tasks;
        public boolean isCyclic;
        public int totalTasks;
        public int completedTasks;
        public float completionPercentage;

        public ChainInfo(String chainId, List<TaskEntity> tasks, boolean isCyclic) {
            this.chainId = chainId;
            this.tasks = tasks;
            this.isCyclic = isCyclic;
            this.totalTasks = tasks.size();
            this.completedTasks = 0;

            for (TaskEntity task : tasks) {
                if (task.completed) {
                    completedTasks++;
                }
            }

            this.completionPercentage = totalTasks > 0 ? (completedTasks / (float) totalTasks) * 100.0f : 0;
        }
    }

    /**
     * Get all tasks in a chain, sorted by chain order
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return Sorted list of tasks in the chain
     */
    public static List<TaskEntity> getTasksInChain(List<TaskEntity> allTasks, String chainId) {
        if (chainId == null || chainId.isEmpty()) {
            return new ArrayList<>();
        }

        List<TaskEntity> chainTasks = new ArrayList<>();
        for (TaskEntity task : allTasks) {
            if (chainId.equals(String.valueOf(task.chainId))) {
                chainTasks.add(task);
            }
        }

        // Sort by chain order
        Collections.sort(chainTasks, new Comparator<TaskEntity>() {
            @Override
            public int compare(TaskEntity a, TaskEntity b) {
                return Integer.compare(a.chainOrder, b.chainOrder);
            }
        });

        return chainTasks;
    }

    /**
     * Get next task in chain after current task
     *
     * @param allTasks All available tasks
     * @param currentTask Current task
     * @return Next task in chain, or null if none
     */
    public static TaskEntity getNextTaskInChain(List<TaskEntity> allTasks, TaskEntity currentTask) {
        if (currentTask.chainId == 0) {
            return null;
        }

        List<TaskEntity> chainTasks = getTasksInChain(allTasks, String.valueOf(currentTask.chainId));
        if (chainTasks.isEmpty()) {
            return null;
        }

        // Find current task index
        int currentIndex = -1;
        for (int i = 0; i < chainTasks.size(); i++) {
            if (chainTasks.get(i).id == currentTask.id) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return null;
        }

        // Get next task (cyclic: wrap around to first)
        int nextIndex = (currentIndex + 1) % chainTasks.size();
        return chainTasks.get(nextIndex);
    }

    /**
     * Get previous task in chain before current task
     *
     * @param allTasks All available tasks
     * @param currentTask Current task
     * @return Previous task in chain, or null if none
     */
    public static TaskEntity getPreviousTaskInChain(List<TaskEntity> allTasks, TaskEntity currentTask) {
        if (currentTask.chainId == 0) {
            return null;
        }

        List<TaskEntity> chainTasks = getTasksInChain(allTasks, String.valueOf(currentTask.chainId));
        if (chainTasks.isEmpty() || chainTasks.size() == 1) {
            return null;
        }

        // Find current task index
        int currentIndex = -1;
        for (int i = 0; i < chainTasks.size(); i++) {
            if (chainTasks.get(i).id == currentTask.id) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1 || currentIndex == 0) {
            return null; // No previous task if first in chain
        }

        return chainTasks.get(currentIndex - 1);
    }

    /**
     * Check if previous task in chain is completed
     *
     * @param allTasks All available tasks
     * @param currentTask Current task
     * @return true if previous task is completed or no previous task exists
     */
    public static boolean isPreviousTaskCompleted(List<TaskEntity> allTasks, TaskEntity currentTask) {
        TaskEntity previous = getPreviousTaskInChain(allTasks, currentTask);
        if (previous == null) {
            return true; // No previous task = no blocker
        }
        return previous.completed;
    }

    /**
     * Check if task is blocked by uncompleted previous task
     *
     * @param allTasks All available tasks
     * @param task Task to check
     * @return true if task is blocked
     */
    public static boolean isTaskBlocked(List<TaskEntity> allTasks, TaskEntity task) {
        if (task.chainId == 0) {
            return false; // Not in a chain = not blocked
        }

        return !isPreviousTaskCompleted(allTasks, task);
    }

    /**
     * Get chain progress percentage
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return Completion percentage (0-100)
     */
    public static float getChainProgress(List<TaskEntity> allTasks, String chainId) {
        List<TaskEntity> chainTasks = getTasksInChain(allTasks, chainId);
        if (chainTasks.isEmpty()) {
            return 0;
        }

        int completed = 0;
        for (TaskEntity task : chainTasks) {
            if (task.completed) {
                completed++;
            }
        }

        return (completed / (float) chainTasks.size()) * 100.0f;
    }

    /**
     * Get all chains from task list
     *
     * @param allTasks All available tasks
     * @return Map of chainId → ChainInfo
     */
    public static Map<String, ChainInfo> getAllChains(List<TaskEntity> allTasks) {
        Map<String, List<TaskEntity>> chainMap = new HashMap<>();

        for (TaskEntity task : allTasks) {
            if (task.chainId != 0) {
                String chainIdStr = String.valueOf(task.chainId);
                if (!chainMap.containsKey(chainIdStr)) {
                    chainMap.put(chainIdStr, new ArrayList<TaskEntity>());
                }
                chainMap.get(chainIdStr).add(task);
            }
        }

        Map<String, ChainInfo> chains = new HashMap<>();
        for (Map.Entry<String, List<TaskEntity>> entry : chainMap.entrySet()) {
            String chainId = entry.getKey();
            List<TaskEntity> tasks = entry.getValue();

            // Sort by chain order
            Collections.sort(tasks, new Comparator<TaskEntity>() {
                @Override
                public int compare(TaskEntity a, TaskEntity b) {
                    return Integer.compare(a.chainOrder, b.chainOrder);
                }
            });

            // Check if cyclic (simple heuristic: if last task's chainOrder + 1 wraps to first)
            boolean isCyclic = tasks.size() > 1;

            chains.put(chainId, new ChainInfo(chainId, tasks, isCyclic));
        }

        return chains;
    }

    /**
     * Get chain description for display
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return Human-readable chain description
     */
    public static String getChainDescription(List<TaskEntity> allTasks, String chainId) {
        List<TaskEntity> chainTasks = getTasksInChain(allTasks, chainId);
        if (chainTasks.isEmpty()) {
            return "Keine Kette";
        }

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < chainTasks.size(); i++) {
            TaskEntity task = chainTasks.get(i);
            description.append(task.title);
            if (i < chainTasks.size() - 1) {
                description.append(" → ");
            }
        }

        // Add cyclic indicator
        if (chainTasks.size() > 1) {
            description.append(" → ↺");
        }

        return description.toString();
    }

    /**
     * Get next available task in chain (first uncompleted task)
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return Next uncompleted task, or null if all completed
     */
    public static TaskEntity getNextAvailableTaskInChain(List<TaskEntity> allTasks, String chainId) {
        List<TaskEntity> chainTasks = getTasksInChain(allTasks, chainId);

        for (TaskEntity task : chainTasks) {
            if (!task.completed) {
                return task;
            }
        }

        return null; // All completed
    }

    /**
     * Reset chain (mark all tasks as incomplete)
     * Useful for cyclic chains
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return List of tasks that were reset
     */
    public static List<TaskEntity> resetChain(List<TaskEntity> allTasks, String chainId) {
        List<TaskEntity> chainTasks = getTasksInChain(allTasks, chainId);
        List<TaskEntity> resetTasks = new ArrayList<>();

        for (TaskEntity task : chainTasks) {
            if (task.completed) {
                task.completed = false;
                task.completedAt = 0;
                resetTasks.add(task);
            }
        }

        return resetTasks;
    }

    /**
     * Get chain visual representation (with completion indicators)
     *
     * @param allTasks All available tasks
     * @param chainId Chain ID
     * @return Visual chain representation like "✓Task1 → Task2 → ✓Task3"
     */
    public static String getChainVisual(List<TaskEntity> allTasks, String chainId) {
        List<TaskEntity> chainTasks = getTasksInChain(allTasks, chainId);
        if (chainTasks.isEmpty()) {
            return "";
        }

        StringBuilder visual = new StringBuilder();
        for (int i = 0; i < chainTasks.size(); i++) {
            TaskEntity task = chainTasks.get(i);

            if (task.completed) {
                visual.append("✓");
            } else {
                visual.append("☐");
            }
            visual.append(task.title);

            if (i < chainTasks.size() - 1) {
                visual.append(" → ");
            }
        }

        return visual.toString();
    }
}
