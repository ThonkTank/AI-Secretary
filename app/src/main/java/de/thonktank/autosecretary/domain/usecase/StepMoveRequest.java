package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepId;

import java.util.Optional;

public final class StepMoveRequest {
    public final TaskStepId stepId;
    public final TaskId targetTaskId;
    public final Optional<TaskStepId> beforeStepId;

    public StepMoveRequest(TaskStepId stepId, TaskId targetTaskId,
                           Optional<TaskStepId> beforeStepId) {
        if (stepId == null || targetTaskId == null || beforeStepId == null)
            throw new IllegalArgumentException("Complete step move is required");
        this.stepId = stepId;
        this.targetTaskId = targetTaskId;
        this.beforeStepId = beforeStepId;
    }
}
