package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class HarvestOccurrence {
    private final TaskRepository repository;
    private final RewardEngine rewards;
    public HarvestOccurrence(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        final RewardReceipt[] result = {RewardReceipt.none()};
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : repository.findTask(occurrence.taskId);
            result[0] = rewards.harvest(occurrence, task);
        });
        return result[0];
    }
}
