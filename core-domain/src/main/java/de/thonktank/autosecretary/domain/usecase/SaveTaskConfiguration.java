package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** The normal task editor cannot create or overwrite flow definitions or shared capacities. */
public final class SaveTaskConfiguration {
    private final TransactionRunner transactions;
    private final CreateTask create;
    private final UpdateTask update;

    public SaveTaskConfiguration(CatalogRepository tasks, StepRepository steps, FlowRepository flows,
                                 TransactionRunner transactions, CreateTask create, UpdateTask update,
                                 IdGenerator ids) {
        this.transactions = transactions; this.create = create; this.update = update;
    }

    public TaskId execute(TaskId taskId, TaskDefinition definition, FlowConfigurationDraft draft) {
        if (definition == null || draft == null || definition.steps.size() != draft.stepKeys.size())
            throw new IllegalArgumentException("Aufgabe und Schritte passen nicht zusammen.");
        if (!draft.links.isEmpty() || !draft.leases.isEmpty()
                || draft.resources.stream().anyMatch(resource -> resource.changed || resource.persistedId == null)
                || definition.steps.stream().anyMatch(step -> step.activationKind == StepActivationKind.FOLLOW_UP))
            throw new IllegalArgumentException("Abläufe werden im Ablaufeditor bearbeitet.");
        return transactions.inTransaction(() -> {
            if (taskId == null) return create.executeInsideTransaction(definition);
            update.executeInsideTransaction(taskId, definition, true);
            return taskId;
        });
    }
}
