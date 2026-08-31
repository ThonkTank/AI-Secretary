package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class DeleteTask {
    private final TaskDefinitionRepository repository;
    private final TransactionRunner transactions;

    public DeleteTask(TaskDefinitionRepository repository, TransactionRunner transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    public void execute(TaskId id) {
        transactions.inTransaction(() -> { repository.deleteTask(id); return null; });
    }
}
