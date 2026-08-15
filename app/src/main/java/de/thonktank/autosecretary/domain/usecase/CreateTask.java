package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public final class CreateTask {
    private final TaskRepository repository;
    private final Clock clock;
    private final IdGenerator ids;
    private final TaskOrdering ordering;

    public CreateTask(TaskRepository repository, Clock clock, IdGenerator ids, TaskOrdering ordering) {
        this.repository = repository;
        this.clock = clock;
        this.ids = ids;
        this.ordering = ordering;
    }

    public void execute(String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                        int weekdayMask, List<String> stepTexts, boolean ongoing, String condition) {
        repository.inTransaction(() -> {
            Task task = Task.create(TaskId.of(ids.nextId()), title, slot, recurrence, intervalDays,
                    weekdayMask, ongoing, condition, clock.today(), 0);
            List<Task> ordered = ordering.insertAtEndOfSlot(repository.allTasks(), task);
            for (Task item : ordered) {
                if (item.id.equals(task.id)) repository.insertTask(item);
                else repository.updateTask(item);
            }
            List<TaskStepTemplate> templates = new ArrayList<>();
            for (int i = 0; i < stepTexts.size(); i++) {
                String text = stepTexts.get(i);
                if (text != null && !text.trim().isEmpty())
                    templates.add(new TaskStepTemplate(ids.nextId(), task.id, i, text));
            }
            repository.insertTemplates(templates);
        });
    }
}
