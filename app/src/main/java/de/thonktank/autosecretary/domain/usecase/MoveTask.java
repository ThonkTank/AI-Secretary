package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Compatibility command delegated to the canonical schedule service. */
public final class MoveTask {
    private final TaskScheduleService schedules;

    public MoveTask(TaskRepository repository, TaskOrdering ignored) {
        schedules = new TaskScheduleService(repository, new UuidGenerator());
    }

    public void execute(TaskId id, TaskSlot slot) {
        TaskScheduleEntry primary;
        try { primary = schedules.load().primary(id); }
        catch (IllegalStateException missingSchedule) { return; }
        schedules.move(primary.id, slot, null);
    }
}
