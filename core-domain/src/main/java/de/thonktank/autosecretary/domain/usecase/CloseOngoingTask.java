package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class CloseOngoingTask {
    private final OccurrenceCompletionService completion;
    public CloseOngoingTask(OccurrenceExecutionRepository occurrences,
                     RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions, Clock clock) {
        completion = new OccurrenceCompletionService(occurrences, rewards, obligations, transactions, clock);
    }
    public RewardReceipt execute(TaskId taskId) { return completion.closeCondition(taskId); }
}
