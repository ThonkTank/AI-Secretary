package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Records the next result of a set-based or single-repetition step. */
public final class RecordRepetitionResult {
    private final CompletionService completion;

    public RecordRepetitionResult(TaskRepository repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }

    public RecordRepetitionResult(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }

    public RewardReceipt execute(String stepId, int repetitions) {
        return completion.recordRepetitionResult(stepId, repetitions);
    }
}
