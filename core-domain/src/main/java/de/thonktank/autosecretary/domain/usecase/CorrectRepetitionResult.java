package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Corrects one persisted result without changing completion rewards. */
public final class CorrectRepetitionResult {
    private final StepExecutionService execution;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CorrectRepetitionResult(T repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CorrectRepetitionResult(T repository, Clock clock) {
        execution = new StepExecutionService(repository, clock);
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    CorrectRepetitionResult(T repository, Clock clock, ComboPolicySource policies) {
        execution = new StepExecutionService(repository, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int index, int repetitions) {
        return execution.correctRepetitionResult(stepId, index, repetitions);
    }
}
