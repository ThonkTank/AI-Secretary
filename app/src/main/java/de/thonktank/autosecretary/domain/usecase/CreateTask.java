package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public final class CreateTask {
    private final TaskRepository repository;
    private final Clock clock;
    private final IdGenerator ids;
    private final TaskOrdering ordering;

    public CreateTask(TaskRepository repository, Clock clock, IdGenerator ids,
                      TaskOrdering ordering) {
        this.repository = repository;
        this.clock = clock;
        this.ids = ids;
        this.ordering = ordering;
    }

    public void execute(TaskDefinition definition) {
        repository.inTransaction(() -> {
            TaskId taskId = TaskId.of(ids.nextId());
            Task task = Task.create(taskId, definition,
                    ScheduleCalculator.firstDue(definition.recurrence,
                            definition.weekdayMask, clock.today()), 0);
            List<Task> ordered = ordering.insertAtEndOfSlot(repository.allTasks(), task);
            for (Task item : ordered) {
                if (item.id.equals(task.id)) repository.insertTask(item);
                else repository.updateTask(item);
            }
            repository.insertTemplates(templates(task.id, definition.steps));
            return null;
        });
    }

    public void execute(String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                        int weekdayMask, List<String> stepTexts, boolean ongoing,
                        String condition) {
        if (ongoing) {
            repository.inTransaction(() -> {
                Task task = Task.create(TaskId.of(ids.nextId()), title, slot, recurrence,
                        intervalDays, weekdayMask, true, condition, clock.today(), 0);
                List<Task> ordered = ordering.insertAtEndOfSlot(repository.allTasks(), task);
                for (Task item : ordered) {
                    if (item.id.equals(task.id)) repository.insertTask(item);
                    else repository.updateTask(item);
                }
                List<TaskStepTemplate> values = new ArrayList<>();
                for (int i = 0; i < stepTexts.size(); i++)
                    if (stepTexts.get(i) != null && !stepTexts.get(i).trim().isEmpty())
                        values.add(new TaskStepTemplate(ids.nextId(), task.id, i, stepTexts.get(i)));
                repository.insertTemplates(values);
                return null;
            });
            return;
        }
        List<TaskStepDefinition> steps = new ArrayList<>();
        for (String value : stepTexts)
            if (value != null && !value.trim().isEmpty())
                steps.add(new TaskStepDefinition(null, steps.size(), value, 0,
                        StepAmount.none(), ""));
        execute(new TaskDefinition(title, null, slot, recurrence, intervalDays, weekdayMask,
                recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit,
                TaskBoundKind.FOREVER, null, null, null, null, "", steps));
    }

    private List<TaskStepTemplate> templates(TaskId taskId,
                                             List<TaskStepDefinition> definitions) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition step = definitions.get(i);
            String id = step.id == null ? ids.nextId() : step.id;
            result.add(new TaskStepTemplate(id, taskId, i, step.text, step.weekdayMask,
                    step.amount, step.note));
        }
        return result;
    }
}
