package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;

/** Loads the complete functional setup screen without exposing persistence to the UI. */
public final class LoadStepFlowSetup {
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final FlowRepository flows;

    public LoadStepFlowSetup(CatalogRepository tasks, StepRepository steps,
                             FlowRepository flows) {
        this.tasks = tasks;
        this.steps = steps;
        this.flows = flows;
    }

    public StepFlowSetup execute(TaskId taskId) {
        Task task = tasks.findTask(taskId);
        if (task == null) return null;
        return new StepFlowSetup(task, steps.templates(taskId), flows.capacityResources(),
                flows.stepTransitions(taskId), flows.stepResourceLeases(taskId));
    }
}
