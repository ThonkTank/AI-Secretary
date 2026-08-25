package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Records the next result of a set-based or single-repetition step. */
public final class RecordRepetitionResult {
    private final StepExecutionService completion;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    RecordRepetitionResult(T repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    RecordRepetitionResult(T repository, Clock clock) {
        completion = new StepExecutionService(repository, clock);
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    RecordRepetitionResult(T repository, Clock clock, ComboPolicySource policies) {
        completion = new StepExecutionService(repository, clock, policies);
    }

    public StepExecutionResult execute(String stepId, int repetitions) {
        return completion.recordRepetitionResult(stepId, repetitions);
    }
}
