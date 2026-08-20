package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.util.List;

public final class MoveTask {
    private final TaskDefinitionRepository repository;
    private final TaskOrdering ordering;

    public MoveTask(TaskDefinitionRepository repository, TaskOrdering ordering) {
        this.repository = repository;
        this.ordering = ordering;
    }

    public void execute(TaskId id, TaskSlot slot) {
        repository.inTransaction(() -> {
            Task task = repository.findTask(id);
            if (task == null) return null;
            List<Task> reordered = ordering.moveToEndOfSlot(
                    repository.allTasks(), id, slot, task.title);
            for (Task item : reordered) repository.updateTask(item);
            return null;
        });
    }
}
