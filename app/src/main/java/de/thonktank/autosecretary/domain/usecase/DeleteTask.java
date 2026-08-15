package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

public final class DeleteTask {
    private final TaskRepository repository;

    public DeleteTask(TaskRepository repository) {
        this.repository = repository;
    }

    public void execute(TaskId id) {
        repository.inTransaction(() -> repository.deleteTask(id));
    }
}
