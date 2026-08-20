package de.thonktank.autosecretary.domain.model;

/** One independently ordered time-of-day placement of a task definition. */
public final class TaskScheduleEntry {
    public final String id;
    public final TaskId taskId;
    public final TaskSlot slot;
    public final long displayOrder;

    public TaskScheduleEntry(String id, TaskId taskId, TaskSlot slot, long displayOrder) {
        if (id == null || id.trim().isEmpty() || taskId == null || slot == null)
            throw new IllegalArgumentException("Schedule identity, task and slot are required");
        this.id = id;
        this.taskId = taskId;
        this.slot = slot;
        this.displayOrder = displayOrder;
    }

    public TaskScheduleEntry move(TaskSlot target, long order) {
        return new TaskScheduleEntry(id, taskId, target, order);
    }

    public TaskScheduleEntry withOrder(long order) {
        return new TaskScheduleEntry(id, taskId, slot, order);
    }
}
