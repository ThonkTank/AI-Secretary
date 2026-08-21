package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.usecase.UuidGenerator;

/** Thin command facade over the canonical schedule service. */
public final class MoveScheduleEntry {
    private final TaskScheduleService schedules;

    public MoveScheduleEntry(TaskScheduleRepository repository) {
        schedules = new TaskScheduleService(repository, new UuidGenerator());
    }

    public ScheduleMoveResult execute(ScheduleMoveRequest request) {
        return schedules.move(request);
    }
}
