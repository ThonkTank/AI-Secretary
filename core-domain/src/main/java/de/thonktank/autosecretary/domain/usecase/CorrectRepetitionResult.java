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

/** Corrects one persisted result without changing completion rewards. */
public final class CorrectRepetitionResult {
    private final StepExecutionService execution;

    public CorrectRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions) {
        this(catalog, steps, today, transactions, new SystemClock(new SystemZoneIdProvider()));
    }

    public CorrectRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions,
                            Clock clock) {
        execution = new StepExecutionService(catalog, steps, today, transactions, clock);
    }

    public CorrectRepetitionResult(CatalogRepository catalog, StepRepository steps, TodayRepository today, TransactionRunner transactions,
                            Clock clock,
                            ComboPolicySource policies) {
        execution = new StepExecutionService(catalog, steps, today, transactions, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int index, int repetitions) {
        return execution.correctRepetitionResult(stepId, index, repetitions);
    }
}
