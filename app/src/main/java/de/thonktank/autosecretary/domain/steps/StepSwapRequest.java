package de.thonktank.autosecretary.domain.steps;

import de.thonktank.autosecretary.domain.model.TaskStepId;

public final class StepSwapRequest {
    public final TaskStepId stepId;
    public final TaskStepId targetStepId;

    public StepSwapRequest(TaskStepId stepId, TaskStepId targetStepId) {
        if (stepId == null || targetStepId == null)
            throw new IllegalArgumentException("Complete step swap is required");
        this.stepId = stepId;
        this.targetStepId = targetStepId;
    }
}
