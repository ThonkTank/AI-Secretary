package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Thin command facade over the canonical schedule service. */
public final class MoveScheduleEntry {
    private final TaskScheduleService schedules;

    public MoveScheduleEntry(TaskRepository repository, Clock ignored) {
        this(repository, new UuidGenerator());
    }

    public MoveScheduleEntry(TaskRepository repository, IdGenerator ids) {
        schedules = new TaskScheduleService(repository, ids);
    }

    public ScheduleMoveResult execute(ScheduleMoveRequest request) {
        return schedules.move(request);
    }
}
