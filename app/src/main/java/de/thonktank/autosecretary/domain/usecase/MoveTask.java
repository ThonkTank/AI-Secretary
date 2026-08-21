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
        execute(id, null, slot);
    }

    public ScheduleMoveResult execute(TaskId id, TaskSlot sourceSlot, TaskSlot slot) {
        TaskScheduleEntry primary;
        try {
            if (sourceSlot == null) primary = schedules.load().primary(id);
            else {
                primary = null;
                for (TaskScheduleEntry entry : schedules.load().placements(id))
                    if (entry.slot == sourceSlot) { primary = entry; break; }
                if (primary == null) return ScheduleMoveResult.NOT_FOUND;
            }
        } catch (IllegalStateException missingSchedule) {
            return ScheduleMoveResult.NOT_FOUND;
        }
        return schedules.move(ScheduleMoveRequest.toEnd(primary.id, slot));
    }
}
