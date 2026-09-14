package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Atomically corrects one repetition result and its reward. */
public final class CorrectSetResult {
    private final TransactionRunner transactions;
    private final StepExecutionService execution;

    public CorrectSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                            TransactionRunner transactions, Clock clock,
                            ComboPolicySource policies) {
        this(catalog, steps, today, transactions, clock, policies, null);
    }

    public CorrectSetResult(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                            TransactionRunner transactions, Clock clock,
                            ComboPolicySource policies, FlowProgression flows) {
        this.transactions = transactions;
        execution = new StepExecutionService(catalog, steps, today, transactions, clock,
                new RewardCalculator(policies), new CompletionStateMachine(), flows);
    }

    public StepExecutionResult execute(String stepId, int index, SetResult value) {
        return transactions.inTransaction(() -> {
            StepExecutionResult result = execution.correctSetResultInsideTransaction(
                    stepId, index, value);
            return result;
        });
    }
}
