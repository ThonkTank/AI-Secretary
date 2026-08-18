package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemClock;
import de.thonktank.autosecretary.SystemZoneIdProvider;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class FinishExercise {
    private final CompletionService completion;
    public FinishExercise(TaskRepository repository) {
        this(repository, new SystemClock(new SystemZoneIdProvider()));
    }
    public FinishExercise(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }
    public RewardReceipt execute(String stepId) { return completion.finishExercise(stepId); }
}
