package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Thin command facade over the canonical schedule service. */
public final class MoveScheduleEntry {
    private final TaskScheduleService schedules;

    public MoveScheduleEntry(TaskScheduleRepository repository, TransactionRunner transactions) {
        schedules = new TaskScheduleService(repository, transactions, new UuidGenerator());
    }

    public ScheduleMoveResult execute(ScheduleMoveRequest request) {
        return schedules.move(request);
    }
}
