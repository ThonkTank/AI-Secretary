package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Owns the single transaction for result, completion, reward and adaptation consequences. */
public final class RecordSetResult {
    private final TrainingRepository training;
    private final TransactionRunner transactions;
    private final StepExecutionService execution;
    private final TrainingAdaptationService adaptation;

    public RecordSetResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards,
                           ComboObligationRepository obligations,
                           TrainingRepository training,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies) {
        this(occurrences, rewards, obligations, training, transactions, clock, ids, policies,
                null);
    }

    public RecordSetResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards,
                           ComboObligationRepository obligations,
                           TrainingRepository training,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies, FlowRuntimeCoordinator flows) {
        this.training = training;
        this.transactions = transactions;
        execution = new StepExecutionService(occurrences, rewards, obligations, transactions, clock,
                new RewardCalculator(policies), new CompletionStateMachine(), flows);
        adaptation = new TrainingAdaptationService(occurrences, training, clock, ids);
    }

    public StepExecutionResult execute(String stepId, SetResult value) {
        return transactions.inTransaction(() -> {
            StepExecutionResult result = execution.recordSetResultInsideTransaction(stepId, value);
            if (result.status == StepExecutionResult.Status.COMPLETED)
                adaptation.evaluate(stepId);
            return result;
        });
    }
}
