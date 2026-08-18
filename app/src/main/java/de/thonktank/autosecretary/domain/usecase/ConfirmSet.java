package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.Clock;

public final class ConfirmSet {
    private final TaskRepository repository;
    private final RewardEngine rewards;

    public ConfirmSet(TaskRepository repository) {
        this(repository, new de.thonktank.autosecretary.SystemClock(
                new de.thonktank.autosecretary.SystemZoneIdProvider()));
    }
    public ConfirmSet(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }

    public RewardResult execute(String stepId, int repetitions) {
        final RewardResult[] result = {RewardResult.none()};
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            OccurrenceStep changed = step.confirmSet(repetitions);
            repository.updateOccurrenceStep(changed);
            if (!step.done && changed.done)
                result[0] = rewards.completeStep(occurrence,
                        repository.findOccurrenceStep(step.id));
        });
        return result[0];
    }
}
