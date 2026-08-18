package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class UndoOccurrence {
    private final CompletionService completion;
    public UndoOccurrence(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.undoOccurrence(occurrenceId);
    }
}
