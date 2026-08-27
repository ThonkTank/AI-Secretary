package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.TaskScheduleService;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.schedule.TaskScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UpdateTask {
    private final TaskDefinitionRepository repository;
    private final TaskScheduleRepository schedules;
    private final IdGenerator ids;
    private final Clock clock;

    public UpdateTask(TaskDefinitionRepository repository, TaskScheduleRepository schedules,
                      IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.schedules = schedules;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute(TaskId id, TaskDefinition definition) {
        repository.inTransaction(() -> {
            Task current = repository.findTask(id);
            if (current == null) return null;
            LocalDate nextDue = current.nextDueOn;
            if (current.recurrence != definition.recurrence
                    || current.weekdayMask != definition.weekdayMask
                    || current.intervalDays != definition.intervalDays) {
                LocalDate today = clock == null ? current.nextDueOn : clock.today();
                if (today != null)
                    nextDue = ScheduleCalculator.firstDue(definition.recurrence,
                            definition.weekdayMask, today);
            }
            repository.updateTask(current.editDefinition(definition, current.catalogOrder, nextDue));
            syncTemplates(id, definition.steps);
            validateRetainedFlow(id);
            new TaskScheduleService(schedules, ids).sync(
                    repository.findTask(id), definition);
            return null;
        });
    }

    private void validateRetainedFlow(TaskId taskId) {
        if (!(repository instanceof StepFlowDefinitionRepository)) return;
        StepFlowDefinitionRepository flows = (StepFlowDefinitionRepository) repository;
        List<TaskStepTemplate> templates = repository.templates(taskId);
        boolean hasFollowUp = false;
        for (TaskStepTemplate template : templates)
            if (template.activationKind == StepActivationKind.FOLLOW_UP) {
                hasFollowUp = true;
                break;
            }
        List<de.thonktank.autosecretary.domain.model.StepTransition> transitions =
                flows.stepTransitions(taskId);
        List<de.thonktank.autosecretary.domain.model.StepResourceLease> leases =
                flows.stepResourceLeases(taskId);
        if (!hasFollowUp && transitions.isEmpty() && leases.isEmpty()) return;
        new StepFlowDefinition(taskId, templates, transitions, leases,
                flows.capacityResources());
    }

    private void syncTemplates(TaskId taskId, List<TaskStepDefinition> definitions) {
        List<TaskStepTemplate> existing = repository.templates(taskId);
        Set<String> existingIds = new HashSet<>();
        for (TaskStepTemplate value : existing) existingIds.add(value.id);
        Set<String> retained = new HashSet<>();
        List<TaskStepTemplate> updated = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition step = definitions.get(i);
            String identity = step.id != null && existingIds.contains(step.id)
                    ? step.id : ids.nextId();
            retained.add(identity);
            updated.add(new TaskStepTemplate(identity, taskId, i, step.text,
                    step.weekdayMask, step.intervalDays, step.amount,
                    step.restTimerPolicy, step.note, step.activationKind));
        }
        for (TaskStepTemplate old : existing)
            if (!retained.contains(old.id)) repository.deleteTemplate(old.id);
        repository.insertTemplates(updated);
    }
}
