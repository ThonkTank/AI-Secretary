package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class CompleteRemainingSteps {
    private final TaskRepository repository;
    private final RewardEngine rewards;
    public CompleteRemainingSteps(TaskRepository repository, Clock clock) {
        this.repository = repository; rewards = new RewardEngine(repository, clock);
    }
    public RewardReceipt execute(String occurrenceId) {
        final int[] total = {0};
        final int[] comboDelta = {0};
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null) return;
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId)) {
                if (step.done) continue;
                RewardReceipt receipt = rewards.completeStep(occurrence, step);
                total[0] += receipt.xp;
                comboDelta[0] += receipt.comboPointDelta;
            }
        });
        return new RewardReceipt(total[0], comboDelta[0],
                RewardReceipt.Target.VESSEL, false);
    }
}
