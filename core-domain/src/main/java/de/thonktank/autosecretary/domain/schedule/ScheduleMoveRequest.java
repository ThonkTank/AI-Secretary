package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.Optional;

public final class ScheduleMoveRequest {
    public final ScheduleEntryId entryId;
    public final TaskSlot targetSlot;
    public final Optional<ScheduleEntryId> beforeEntryId;

    public ScheduleMoveRequest(ScheduleEntryId entryId, TaskSlot targetSlot,
                               Optional<ScheduleEntryId> beforeEntryId) {
        if (entryId == null || targetSlot == null || beforeEntryId == null)
            throw new IllegalArgumentException("Complete schedule move is required");
        this.entryId = entryId;
        this.targetSlot = targetSlot;
        this.beforeEntryId = beforeEntryId;
    }

    public static ScheduleMoveRequest toEnd(String entryId, TaskSlot targetSlot) {
        return new ScheduleMoveRequest(ScheduleEntryId.of(entryId), targetSlot, Optional.empty());
    }
}
