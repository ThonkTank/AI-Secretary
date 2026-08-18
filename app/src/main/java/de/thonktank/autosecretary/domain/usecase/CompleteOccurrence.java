package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CompleteOccurrence {
    private final TaskRepository repository;
    private final RewardEngine rewards;

    public CompleteOccurrence(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.rewards = new RewardEngine(repository, clock);
    }

    public RewardReceipt execute(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return RewardReceipt.none();
        final RewardReceipt[] result = {RewardReceipt.none()};
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            Task task = repository.findTask(occurrence.taskId);
            if (task == null) return;
            for (de.thonktank.autosecretary.domain.model.OccurrenceStep step
                    : repository.occurrenceSteps(occurrenceId))
                if (!step.done) rewards.completeStep(occurrence, step);
            result[0] = rewards.harvest(repository.findOccurrence(occurrenceId), task);
        });
        return result[0];
    }
}
