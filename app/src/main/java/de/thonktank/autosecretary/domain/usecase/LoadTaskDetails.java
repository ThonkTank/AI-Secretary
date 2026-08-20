package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

public final class LoadTaskDetails {
    private final TaskDefinitionRepository repository;

    public LoadTaskDetails(TaskDefinitionRepository repository) {
        this.repository = repository;
    }

    public TaskDetails execute(TaskId id) {
        Task task = repository.findTask(id);
        return task == null ? null : new TaskDetails(task, repository.templates(id));
    }
}
