package de.thonktank.autosecretary.domain.usecase;

import java.util.List;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Explicitly reopens a completed exercise while preserving its editable set progress. */
public final class ReopenExercise {
    private final CompletionService completion;
    public ReopenExercise(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }
    public RewardReceipt execute(String stepId, List<Integer> repetitions) {
        return completion.reopenExercise(stepId, repetitions);
    }
}
