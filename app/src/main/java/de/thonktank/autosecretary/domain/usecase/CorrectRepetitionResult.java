package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Corrects one persisted result without changing completion rewards. */
public final class CorrectRepetitionResult {
    private final StepExecutionService execution;

    public CorrectRepetitionResult(TaskRepository repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }

    public CorrectRepetitionResult(TaskRepository repository, Clock clock) {
        execution = new StepExecutionService(repository, clock);
    }

    public StepExecutionResult execute(String stepId, int index, int repetitions) {
        return execution.correctRepetitionResult(stepId, index, repetitions);
    }
}
