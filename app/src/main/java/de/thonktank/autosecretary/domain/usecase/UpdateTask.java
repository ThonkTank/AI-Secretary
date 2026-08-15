package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.List;

public final class UpdateTask {
    private final TaskRepository repository;
    private final TaskOrdering ordering;

    public UpdateTask(TaskRepository repository, TaskOrdering ordering) {
        this.repository = repository;
        this.ordering = ordering;
    }

    public void execute(TaskId id, String title, TaskSlot slot) {
        repository.inTransaction(() -> {
            Task current = repository.findTask(id);
            if (current == null) return;
            if (current.slot == slot) {
                repository.updateTask(current.edit(title, slot, current.displayOrder));
                return;
            }
            List<Task> reordered = ordering.moveToEndOfSlot(repository.allTasks(), id, slot, title);
            for (Task task : reordered) repository.updateTask(task);
        });
    }
}
