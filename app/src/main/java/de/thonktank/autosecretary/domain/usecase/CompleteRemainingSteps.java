package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CompleteRemainingSteps {
    private final TaskRepository repository;
    private final RewardEngine rewards;
    public CompleteRemainingSteps(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }
    public RewardResult execute(String occurrenceId) {
        final int[] total = {0};
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null) return;
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
                if (!step.done) total[0] += rewards.completeStep(occurrence, step).xp;
        });
        return new RewardResult(total[0], RewardResult.Target.VESSEL, false);
    }
}
