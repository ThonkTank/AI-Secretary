package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Atomically saves derived step roles and graph rules for future flow runs. */
public final class SaveStepFlowSetup {
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final FlowRepository flows;
    private final TransactionRunner transactions;

    public SaveStepFlowSetup(CatalogRepository tasks, StepRepository steps,
                             FlowRepository flows,
                             TransactionRunner transactions) {
        this.tasks = tasks;
        this.steps = steps;
        this.flows = flows;
        this.transactions = transactions;
    }

    public StepFlowDefinition execute(TaskId taskId,
                                      Map<String, StepActivationKind> activationByStep,
                                      List<StepTransition> transitions,
                                      List<StepResourceLease> leases) {
        if (taskId == null || activationByStep == null || transitions == null || leases == null)
            throw new IllegalArgumentException("Ablaufeinrichtung ist unvollständig");
        return transactions.inTransaction(() -> {
            if (tasks.findTask(taskId) == null)
                throw new IllegalArgumentException("Aufgabe existiert nicht");
            List<TaskStepTemplate> updated = new ArrayList<>();
            for (TaskStepTemplate current : steps.templates(taskId)) {
                StepActivationKind activation = activationByStep.getOrDefault(current.id,
                        StepActivationKind.SCHEDULED);
                updated.add(new TaskStepTemplate(current.id, current.taskId, current.position,
                        current.text,
                        activation == StepActivationKind.FOLLOW_UP ? 0 : current.weekdayMask,
                        activation == StepActivationKind.FOLLOW_UP ? 0 : current.intervalDays,
                        current.prescription, current.assistantProfile, current.note, activation));
            }
            StepFlowDefinition definition = new StepFlowDefinition(taskId, updated, transitions,
                    leases, flows.capacityResources());
            steps.insertTemplates(updated);
            flows.replaceStepFlow(taskId, transitions, leases);
            return definition;
        });
    }
}
