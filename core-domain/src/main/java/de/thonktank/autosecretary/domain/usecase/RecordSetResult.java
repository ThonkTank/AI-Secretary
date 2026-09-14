package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Owns the single transaction for result, completion and reward consequences. */
public final class RecordSetResult {
    private final TransactionRunner transactions;
    private final StepExecutionService execution;

    public RecordSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies) {
        this(catalog, steps, today, transactions, clock, ids, policies,
                null);
    }

    public RecordSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                           TransactionRunner transactions, Clock clock, IdGenerator ids,
                           ComboPolicySource policies, FlowProgression flows) {
        this.transactions = transactions;
        execution = new StepExecutionService(catalog, steps, today, transactions, clock,
                new RewardCalculator(policies), new CompletionStateMachine(), flows);
    }

    public StepExecutionResult execute(String stepId, SetResult value) {
        return transactions.inTransaction(() -> {
            StepExecutionResult result = execution.recordSetResultInsideTransaction(stepId, value);
            return result;
        });
    }
}
