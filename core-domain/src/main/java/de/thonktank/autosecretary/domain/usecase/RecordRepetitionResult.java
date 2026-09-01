package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Records the next result of a set-based or single-repetition step. */
public final class RecordRepetitionResult {
    private final StepExecutionService completion;

    public RecordRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions) {
        this(catalog, steps, today, transactions, new SystemClock(new SystemZoneIdProvider()));
    }

    public RecordRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions,
                           Clock clock) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock);
    }

    public RecordRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies);
    }

    public RecordRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies,
                           FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(catalog, steps, today, transactions, clock, policies,
                flows);
    }

    public StepExecutionResult execute(String stepId, int repetitions) {
        return completion.recordRepetitionResult(stepId, repetitions);
    }
}
