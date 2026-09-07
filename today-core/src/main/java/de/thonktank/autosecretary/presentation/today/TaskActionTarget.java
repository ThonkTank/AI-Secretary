package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Minimal immutable task identity and metadata needed by menus and dialogs. */
public final class TaskActionTarget {
    public final String taskId;
    public final TodayItemTarget item;
    public final String title;
    public final TaskSlot slot;
    public final boolean routine;
    public final boolean terminalCondition;

    private TaskActionTarget(String taskId, TodayItemTarget item, String title, TaskSlot slot,
                             boolean routine, boolean terminalCondition) {
        if (taskId == null || taskId.isEmpty() || item == null || title == null
                || title.trim().isEmpty() || slot == null)
            throw new IllegalArgumentException("Task action identity is required");
        this.taskId = taskId;
        this.item = item;
        this.title = title;
        this.slot = slot;
        this.routine = routine;
        this.terminalCondition = terminalCondition;
    }

    public static TaskActionTarget of(String taskId, TodayItemTarget item, String title,
                                      TaskSlot slot, boolean routine,
                                      boolean terminalCondition) {
        return new TaskActionTarget(taskId, item, title, slot, routine,
                terminalCondition);
    }
}
