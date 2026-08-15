package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.List;
import java.util.ArrayList;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

public final class UpdateTask {
    private final TaskRepository repository;
    private final TaskOrdering ordering;
    private final IdGenerator ids;

    public UpdateTask(TaskRepository repository, TaskOrdering ordering) {
        this(repository, ordering, new UuidGenerator());
    }

    public UpdateTask(TaskRepository repository, TaskOrdering ordering, IdGenerator ids) {
        this.repository = repository;
        this.ordering = ordering;
        this.ids = ids;
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

    public void execute(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                        int intervalDays, int weekdayMask, List<String> stepTexts,
                        boolean ongoing, String condition) {
        repository.inTransaction(() -> {
            Task current = repository.findTask(id);
            if (current == null) return;
            long displayOrder = current.displayOrder;
            if (current.slot != slot) {
                List<Task> reordered = ordering.moveToEndOfSlot(repository.allTasks(), id, slot, title);
                for (Task task : reordered) {
                    if (task.id.equals(id)) displayOrder = task.displayOrder;
                    else repository.updateTask(task);
                }
            }
            Task edited = current.editDefinition(title, slot, recurrence, intervalDays,
                    weekdayMask, ongoing, condition, displayOrder);
            repository.updateTask(edited);
            repository.deleteTemplates(id);
            List<TaskStepTemplate> templates = new ArrayList<>();
            for (int i = 0; i < stepTexts.size(); i++) {
                String text = stepTexts.get(i);
                if (text != null && !text.trim().isEmpty())
                    templates.add(new TaskStepTemplate(ids.nextId(), id, i, text));
            }
            repository.insertTemplates(templates);
        });
    }
}
