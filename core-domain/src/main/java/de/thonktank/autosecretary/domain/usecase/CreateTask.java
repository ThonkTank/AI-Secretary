package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.util.ArrayList;
import java.util.List;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

public final class CreateTask {
    private static final long CATALOG_ORDER_STEP = 1_024L;
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final IdGenerator ids;

    public CreateTask(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                      TransactionRunner transactions, Clock clock, IdGenerator ids) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public TaskId execute(TaskDefinition definition) {
        return transactions.inTransaction(() -> executeInsideTransaction(definition));
    }

    TaskId executeInsideTransaction(TaskDefinition definition) {
        TaskId taskId = TaskId.of(ids.nextId());
        Task task = Task.create(taskId, definition,
                ScheduleCalculator.firstDue(definition.recurrence,
                        definition.weekdayMask, clock.today()), nextCatalogOrder());
        catalog.insertTask(task);
        steps.insertTemplates(templates(task.id, definition.steps));
        new TaskScheduleService(catalog, today, transactions, ids).create(task, definition);
        return taskId;
    }

    private long nextCatalogOrder() {
        long last = 0;
        for (Task task : catalog.allTasks()) last = Math.max(last, task.catalogOrder);
        return last + CATALOG_ORDER_STEP;
    }

    private List<TaskStepTemplate> templates(TaskId taskId,
                                             List<TaskStepDefinition> definitions) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition step = definitions.get(i);
            String id = step.id == null ? ids.nextId() : step.id;
            de.thonktank.autosecretary.domain.model.TrainingAssistantProfile profile =
                    step.assistantPolicy == null ? null
                            : new de.thonktank.autosecretary.domain.model.TrainingAssistantProfile(
                            step.assistantPolicy,
                            de.thonktank.autosecretary.domain.model.TrainingAssistantState.calibrating());
            result.add(new TaskStepTemplate(id, taskId, i, step.text, step.weekdayMask,
                    step.intervalDays, step.prescription, profile, step.note,
                    step.activationKind));
        }
        return result;
    }
}
