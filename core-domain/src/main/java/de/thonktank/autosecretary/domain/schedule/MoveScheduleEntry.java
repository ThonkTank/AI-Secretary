package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.usecase.UuidGenerator;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

/** Thin command facade over the canonical schedule service. */
public final class MoveScheduleEntry {
    private final TaskScheduleService schedules;

    public MoveScheduleEntry(CatalogRepository catalog, TodayRepository today,
                             TransactionRunner transactions) {
        schedules = new TaskScheduleService(catalog, today, transactions, new UuidGenerator());
    }

    public ScheduleMoveResult execute(ScheduleMoveRequest request) {
        return schedules.move(request);
    }
}
