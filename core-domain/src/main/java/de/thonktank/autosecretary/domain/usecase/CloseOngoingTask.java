package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class CloseOngoingTask {
    private final OccurrenceCompletionService completion;
    public CloseOngoingTask(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions, Clock clock) {
        completion = new OccurrenceCompletionService(catalog, steps, today, transactions, clock);
    }
    public RewardReceipt execute(TaskId taskId) { return completion.closeCondition(taskId); }
}
