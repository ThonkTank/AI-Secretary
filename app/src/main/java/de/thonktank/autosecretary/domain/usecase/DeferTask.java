package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.List;

public final class DeferTask {
    private final TaskRepository repository;
    private final TaskOrdering ordering;

    public DeferTask(TaskRepository repository, LoadDashboard ignored,
                     TaskOrdering ordering, Clock clock) {
        this.repository = repository;
        this.ordering = ordering;
    }

    public void execute(String occurrenceOrTaskId) {
        Occurrence selected = repository.findOccurrence(occurrenceOrTaskId);
        if (selected != null) {
            repository.inTransaction(() -> {
                Occurrence current = repository.findOccurrence(occurrenceOrTaskId);
                if (current == null) return null;
                int last = current.sortOrder;
                for (Occurrence occurrence : repository.openOccurrences())
                    if (occurrence.slot == current.slot) last = Math.max(last, occurrence.sortOrder);
                if (last > current.sortOrder) repository.updateOccurrence(current.moveTo(last + 1));
                return null;
            });
            return;
        }
        TaskId id;
        try { id = TaskId.of(occurrenceOrTaskId); }
        catch (IllegalArgumentException error) { return; }
        repository.inTransaction(() -> {
            List<Task> tasks = ordering.sorted(repository.allTasks());
            int index = -1;
            for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id.equals(id)) index = i;
            if (index < 0 || index >= tasks.size() - 1) return null;
            for (Task task : ordering.swap(tasks, id, tasks.get(index + 1).id))
                repository.updateTask(task);
            return null;
        });
    }
}
