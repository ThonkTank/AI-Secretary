package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Owns the single transaction for result, completion, reward and adaptation consequences. */
public final class RecordSetResult {
    private final TrainingRepository training;
    private final TransactionRunner transactions;
    private final StepExecutionService execution;
    private final TrainingAdaptationService adaptation;

    public RecordSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                           TrainingRepository training,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies) {
        this(catalog, steps, today, training, transactions, clock, ids, policies,
                null);
    }

    public RecordSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                           TrainingRepository training,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies, FlowRuntimeCoordinator flows) {
        this.training = training;
        this.transactions = transactions;
        execution = new StepExecutionService(catalog, steps, today, transactions, clock,
                new RewardCalculator(policies), new CompletionStateMachine(), flows);
        adaptation = new TrainingAdaptationService(steps, training, clock, ids);
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
