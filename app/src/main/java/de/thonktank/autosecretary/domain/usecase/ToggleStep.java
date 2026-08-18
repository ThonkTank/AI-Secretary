package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.model.StepAmountKind;

public final class ToggleStep {
    private final TaskRepository repository;
    private final RewardEngine rewards;

    public ToggleStep(TaskRepository repository) {
        this(repository, new de.thonktank.autosecretary.SystemClock(
                new de.thonktank.autosecretary.SystemZoneIdProvider()));
    }

    public ToggleStep(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.rewards = new RewardEngine(repository, clock);
    }

    public RewardReceipt execute(String stepId) {
        final RewardReceipt[] result = {RewardReceipt.none()};
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence != null && occurrence.state == OccurrenceState.OPEN
                    && step.amountKind != StepAmountKind.SETS_REPS)
                result[0] = step.done ? rewards.undoStep(occurrence, step)
                        : rewards.completeStep(occurrence, step);
        });
        return result[0];
    }
}
