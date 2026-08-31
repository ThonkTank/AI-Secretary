package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Corrects one persisted result without changing completion rewards. */
public final class CorrectRepetitionResult {
    private final StepExecutionService execution;

    public CorrectRepetitionResult(OccurrenceExecutionRepository occurrences,
                            RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions) {
        this(occurrences, rewards, obligations, transactions, new SystemClock(new SystemZoneIdProvider()));
    }

    public CorrectRepetitionResult(OccurrenceExecutionRepository occurrences,
                            RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                            Clock clock) {
        execution = new StepExecutionService(occurrences, rewards, obligations, transactions, clock);
    }

    public CorrectRepetitionResult(OccurrenceExecutionRepository occurrences,
                            RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                            Clock clock,
                            ComboPolicySource policies) {
        execution = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int index, int repetitions) {
        return execution.correctRepetitionResult(stepId, index, repetitions);
    }
}
