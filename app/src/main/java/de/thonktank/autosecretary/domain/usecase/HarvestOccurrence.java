package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class HarvestOccurrence {
    private final CompletionService completion;
    public HarvestOccurrence(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        return completion.harvestOccurrence(occurrenceId);
    }
}
