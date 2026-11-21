package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;

/** Corrects a saved set result and its reward. */
public final class CorrectSetResult {
    private final StepExecutionService execution;

    public CorrectSetResult(StepExecutionService execution) {
        this.execution = execution;
    }

    public StepExecutionResult execute(String stepId, int index, SetResult value) {
        return execution.correctSetResult(stepId, index, value);
    }
}
