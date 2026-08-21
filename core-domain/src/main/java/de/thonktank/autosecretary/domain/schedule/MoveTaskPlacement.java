package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.UuidGenerator;

/** Resolves a Today placement and delegates its mutation to the canonical schedule service. */
public final class MoveTaskPlacement {
    private final TaskScheduleRepository repository;
    private final TaskScheduleService schedules;

    public MoveTaskPlacement(TaskScheduleRepository repository) {
        this.repository = repository;
        schedules = new TaskScheduleService(repository, new UuidGenerator());
    }

    public ScheduleMoveResult execute(TaskId id, TaskSlot sourceSlot, TaskSlot slot) {
        TaskScheduleEntry selected = null;
        for (TaskScheduleEntry entry : repository.scheduleEntries(id))
            if (sourceSlot == null || entry.slot == sourceSlot) { selected = entry; break; }
        return selected == null ? ScheduleMoveResult.NOT_FOUND
                : schedules.move(ScheduleMoveRequest.toEnd(selected.id, slot));
    }
}
