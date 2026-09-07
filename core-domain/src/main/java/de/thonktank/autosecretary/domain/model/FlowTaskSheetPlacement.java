package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Stable Today identity and order for all flow work of one task and slot. */
public final class FlowTaskSheetPlacement {
    public final String id;
    public final TaskId taskId;
    public final TaskSlot slot;
    public final LocalDate displayOn;
    public final int sortOrder;

    public FlowTaskSheetPlacement(String id, TaskId taskId, TaskSlot slot,
                                  LocalDate displayOn, int sortOrder) {
        if (id == null || id.trim().isEmpty() || taskId == null || slot == null
                || displayOn == null || sortOrder < 0)
            throw new IllegalArgumentException("Flow task sheet placement is incomplete");
        this.id = id;
        this.taskId = taskId;
        this.slot = slot;
        this.displayOn = displayOn;
        this.sortOrder = sortOrder;
    }

    public static String stableId(TaskId taskId, TaskSlot slot) {
        return "flow-task-sheet:" + taskId.value + ':' + slot.storageCode;
    }

    public FlowTaskSheetPlacement moveTo(LocalDate date, int order) {
        return new FlowTaskSheetPlacement(id, taskId, slot, date, order);
    }
}
