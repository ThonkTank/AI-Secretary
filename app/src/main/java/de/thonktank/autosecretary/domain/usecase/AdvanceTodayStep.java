package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Advances a visible non-active step with its planned value and focuses it when still open. */
public final class AdvanceTodayStep {
    private final CompletionService completion;

    public AdvanceTodayStep(TaskRepository repository, Clock clock) {
        completion = new CompletionService(repository, clock);
    }

    public RewardReceipt execute(String stepId) {
        return completion.advanceStepWithPlannedResult(stepId);
    }
}
