package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.List;

/** Validates the complete graph before atomically replacing a task's flow rules. */
public final class SaveStepFlowDefinition {
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final FlowRepository flows;
    private final TransactionRunner transactions;

    public SaveStepFlowDefinition(CatalogRepository tasks, StepRepository steps,
                                  FlowRepository flows,
                                  TransactionRunner transactions) {
        this.tasks = tasks;
        this.steps = steps;
        this.flows = flows;
        this.transactions = transactions;
    }

    public StepFlowDefinition execute(TaskId taskId, List<StepTransition> transitions,
                                      List<StepResourceLease> leases) {
        if (tasks.findTask(taskId) == null)
            throw new IllegalArgumentException("Aufgabe existiert nicht");
        List<TaskStepTemplate> templates = steps.templates(taskId);
        StepFlowDefinition definition = new StepFlowDefinition(taskId, templates, transitions,
                leases, flows.capacityResources());
        transactions.inTransaction(() -> {
            flows.replaceStepFlow(taskId, transitions, leases);
            return null;
        });
        return definition;
    }
}
