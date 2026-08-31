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

/** Records the next result of a set-based or single-repetition step. */
public final class RecordRepetitionResult {
    private final StepExecutionService completion;

    public RecordRepetitionResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions) {
        this(occurrences, rewards, obligations, transactions, new SystemClock(new SystemZoneIdProvider()));
    }

    public RecordRepetitionResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock);
    }

    public RecordRepetitionResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies);
    }

    public RecordRepetitionResult(OccurrenceExecutionRepository occurrences,
                           RewardLedgerRepository rewards, ComboObligationRepository obligations, TransactionRunner transactions,
                           Clock clock,
                           ComboPolicySource policies,
                           FlowRuntimeCoordinator flows) {
        completion = new StepExecutionService(occurrences, rewards, obligations, transactions, clock, policies,
                flows);
    }

    public StepExecutionResult execute(String stepId, int repetitions) {
        return completion.recordRepetitionResult(stepId, repetitions);
    }
}
