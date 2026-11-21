package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Saves the scheduled task fields and steps owned by the normal task editor. */
public final class SaveTaskConfiguration {
    private final TransactionRunner transactions;
    private final CreateTask create;
    private final UpdateTask update;

    public SaveTaskConfiguration(TransactionRunner transactions, CreateTask create, UpdateTask update) {
        this.transactions = transactions;
        this.create = create;
        this.update = update;
    }

    public TaskId execute(TaskId taskId, TaskDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Aufgabenentwurf fehlt.");
        if (definition.steps.stream().anyMatch(step -> step.activationKind == StepActivationKind.FOLLOW_UP))
            throw new IllegalArgumentException("Abläufe werden im Ablaufeditor bearbeitet.");
        return transactions.inTransaction(() -> {
            if (taskId == null) return create.executeInsideTransaction(definition);
            update.executeInsideTransaction(taskId, definition, true);
            return taskId;
        });
    }
}
