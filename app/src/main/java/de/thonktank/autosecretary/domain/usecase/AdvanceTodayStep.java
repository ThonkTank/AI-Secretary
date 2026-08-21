package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;

/** Advances a visible non-active step with its planned value and focuses it when still open. */
public final class AdvanceTodayStep {
    private final StepExecutionService completion;

    public AdvanceTodayStep(TaskRepository repository, Clock clock) {
        completion = new StepExecutionService(repository, clock);
    }

    public AdvanceTodayStepResult execute(String stepId) {
        return completion.advanceStepWithPlannedResult(stepId);
    }
}
