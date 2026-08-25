package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

/** Loads the complete functional setup screen without exposing persistence to the UI. */
public final class LoadStepFlowSetup {
    private final TaskDefinitionRepository tasks;
    private final StepFlowDefinitionRepository flows;

    public LoadStepFlowSetup(TaskDefinitionRepository tasks,
                             StepFlowDefinitionRepository flows) {
        this.tasks = tasks;
        this.flows = flows;
    }

    public StepFlowSetup execute(TaskId taskId) {
        Task task = tasks.findTask(taskId);
        if (task == null) return null;
        return new StepFlowSetup(task, tasks.templates(taskId), flows.capacityResources(),
                flows.stepTransitions(taskId), flows.stepResourceLeases(taskId));
    }
}
