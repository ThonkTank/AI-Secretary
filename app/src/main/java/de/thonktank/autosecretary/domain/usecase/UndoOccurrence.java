package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class UndoOccurrence {
    private final TaskRepository repository;
    private final RewardEngine rewards;
    public UndoOccurrence(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }
    public RewardResult execute(String occurrenceId) {
        final RewardResult[] result = {RewardResult.none()};
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : repository.findTask(occurrence.taskId);
            result[0] = rewards.undoHarvest(occurrence, task);
        });
        return result[0];
    }
}
