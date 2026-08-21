package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

public final class DeleteTask {
    private final TaskDefinitionRepository repository;

    public DeleteTask(TaskDefinitionRepository repository) {
        this.repository = repository;
    }

    public void execute(TaskId id) {
        repository.inTransaction(() -> { repository.deleteTask(id); return null; });
    }
}
