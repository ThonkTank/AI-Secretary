package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Records the next set result and its reward. */
public final class RecordSetResult {
    private final StepExecutionService execution;

    public RecordSetResult(StepExecutionService execution) {
        this.execution = execution;
    }

    public StepExecutionResult execute(String stepId, SetResult value) {
        return execution.recordSetResult(stepId, value);
    }
}
