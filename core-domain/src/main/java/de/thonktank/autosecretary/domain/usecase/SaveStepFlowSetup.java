package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Atomically saves derived step roles and graph rules for future flow runs. */
public final class SaveStepFlowSetup {
    private final TaskDefinitionRepository tasks;
    private final StepFlowDefinitionRepository flows;

    public SaveStepFlowSetup(TaskDefinitionRepository tasks,
                             StepFlowDefinitionRepository flows) {
        this.tasks = tasks;
        this.flows = flows;
    }

    public StepFlowDefinition execute(TaskId taskId,
                                      Map<String, StepActivationKind> activationByStep,
                                      List<StepTransition> transitions,
                                      List<StepResourceLease> leases) {
        if (taskId == null || activationByStep == null || transitions == null || leases == null)
            throw new IllegalArgumentException("Ablaufeinrichtung ist unvollständig");
        return flows.inTransaction(() -> {
            if (tasks.findTask(taskId) == null)
                throw new IllegalArgumentException("Aufgabe existiert nicht");
            List<TaskStepTemplate> updated = new ArrayList<>();
            for (TaskStepTemplate current : tasks.templates(taskId)) {
                StepActivationKind activation = activationByStep.getOrDefault(current.id,
                        StepActivationKind.SCHEDULED);
                updated.add(new TaskStepTemplate(current.id, current.taskId, current.position,
                        current.text,
                        activation == StepActivationKind.FOLLOW_UP ? 0 : current.weekdayMask,
                        activation == StepActivationKind.FOLLOW_UP ? 0 : current.intervalDays,
                        current.amount, current.restTimerPolicy, current.note, activation));
            }
            StepFlowDefinition definition = new StepFlowDefinition(taskId, updated, transitions,
                    leases, flows.capacityResources());
            tasks.insertTemplates(updated);
            flows.replaceStepFlow(taskId, transitions, leases);
            return definition;
        });
    }
}
