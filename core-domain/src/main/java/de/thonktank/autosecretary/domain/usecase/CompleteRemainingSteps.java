package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class CompleteRemainingSteps {
    private final OccurrenceCompletionService completion;
    public CompleteRemainingSteps(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock) {
        completion = new OccurrenceCompletionService(occurrences, rewards, obligations, transactions, clock);
    }
    public CompleteRemainingSteps(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies) {
        completion = new OccurrenceCompletionService(occurrences, rewards, obligations, transactions, clock,
                policies);
    }
    public CompleteRemainingSteps(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies,
                           FlowRuntimeCoordinator flows) {
        completion = new OccurrenceCompletionService(occurrences, rewards, obligations, transactions, clock,
                policies, flows);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.completeRemainingSteps(occurrenceId);
    }
}
