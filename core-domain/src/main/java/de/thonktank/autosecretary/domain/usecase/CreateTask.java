package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;

import java.util.ArrayList;
import java.util.List;

public final class CreateTask {
    private static final long CATALOG_ORDER_STEP = 1_024L;
    private final TaskDefinitionRepository repository;
    private final TaskScheduleRepository schedules;
    private final Clock clock;
    private final IdGenerator ids;

    public CreateTask(TaskDefinitionRepository repository, TaskScheduleRepository schedules,
                      Clock clock, IdGenerator ids) {
        this.repository = repository;
        this.schedules = schedules;
        this.clock = clock;
        this.ids = ids;
    }

    public TaskId execute(TaskDefinition definition) {
        return repository.inTransaction(() -> executeInsideTransaction(definition));
    }

    TaskId executeInsideTransaction(TaskDefinition definition) {
        TaskId taskId = TaskId.of(ids.nextId());
        Task task = Task.create(taskId, definition,
                ScheduleCalculator.firstDue(definition.recurrence,
                        definition.weekdayMask, clock.today()), nextCatalogOrder());
        repository.insertTask(task);
        repository.insertTemplates(templates(task.id, definition.steps));
        new TaskScheduleService(schedules, ids).create(task, definition);
        return taskId;
    }

    private long nextCatalogOrder() {
        long last = 0;
        for (Task task : repository.allTasks()) last = Math.max(last, task.catalogOrder);
        return last + CATALOG_ORDER_STEP;
    }

    private List<TaskStepTemplate> templates(TaskId taskId,
                                             List<TaskStepDefinition> definitions) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition step = definitions.get(i);
            String id = step.id == null ? ids.nextId() : step.id;
            result.add(new TaskStepTemplate(id, taskId, i, step.text, step.weekdayMask,
                    step.intervalDays, step.amount, step.restTimerPolicy, step.trainingAssistant,
                    step.trainingAssistant.enabled
                            ? de.thonktank.autosecretary.domain.model.TrainingAssistantState.calibrating()
                            : de.thonktank.autosecretary.domain.model.TrainingAssistantState.disabled(),
                    step.note, step.activationKind));
        }
        return result;
    }
}
