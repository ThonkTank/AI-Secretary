package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.util.List;

/** Validates the complete graph before atomically replacing a task's flow rules. */
public final class SaveStepFlowDefinition {
    private final TaskDefinitionRepository tasks;
    private final StepFlowDefinitionRepository flows;

    public SaveStepFlowDefinition(TaskDefinitionRepository tasks,
                                  StepFlowDefinitionRepository flows) {
        this.tasks = tasks;
        this.flows = flows;
    }

    public StepFlowDefinition execute(TaskId taskId, List<StepTransition> transitions,
                                      List<StepResourceLease> leases) {
        if (tasks.findTask(taskId) == null)
            throw new IllegalArgumentException("Aufgabe existiert nicht");
        List<TaskStepTemplate> templates = tasks.templates(taskId);
        StepFlowDefinition definition = new StepFlowDefinition(taskId, templates, transitions,
                leases, flows.capacityResources());
        flows.inTransaction(() -> {
            flows.replaceStepFlow(taskId, transitions, leases);
            return null;
        });
        return definition;
    }
}
