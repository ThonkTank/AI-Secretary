package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

/** Resolves a Today placement and delegates its mutation to the canonical schedule service. */
public final class MoveTaskPlacement {
    private final CatalogRepository repository;
    private final TaskScheduleService schedules;

    public MoveTaskPlacement(CatalogRepository repository, TodayRepository today,
                             TransactionRunner transactions) {
        this.repository = repository;
        schedules = new TaskScheduleService(repository, today, transactions, new UuidGenerator());
    }

    public ScheduleMoveResult execute(TaskId id, TaskSlot sourceSlot, TaskSlot slot) {
        TaskScheduleEntry selected = null;
        for (TaskScheduleEntry entry : repository.scheduleEntries(id))
            if (sourceSlot == null || entry.slot == sourceSlot) { selected = entry; break; }
        return selected == null ? ScheduleMoveResult.NOT_FOUND
                : schedules.move(ScheduleMoveRequest.toEnd(selected.id, slot));
    }
}
