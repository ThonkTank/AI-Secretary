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
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UpdateTask {
    private final TaskDefinitionRepository repository;
    private final TaskOrdering ordering;
    private final IdGenerator ids;
    private final Clock clock;

    public UpdateTask(TaskDefinitionRepository repository, TaskOrdering ordering) {
        this(repository, ordering, new UuidGenerator(), null);
    }

    public UpdateTask(TaskDefinitionRepository repository, TaskOrdering ordering, IdGenerator ids) {
        this(repository, ordering, ids, null);
    }

    public UpdateTask(TaskDefinitionRepository repository, TaskOrdering ordering, IdGenerator ids,
                      Clock clock) {
        this.repository = repository;
        this.ordering = ordering;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute(TaskId id, String title, TaskSlot slot) {
        repository.inTransaction(() -> {
            Task current = repository.findTask(id);
            if (current == null) return null;
            if (current.slot == slot) {
                repository.updateTask(current.edit(title, slot, current.displayOrder));
                return null;
            }
            List<Task> reordered = ordering.moveToEndOfSlot(repository.allTasks(), id, slot, title);
            for (Task task : reordered) repository.updateTask(task);
            return null;
        });
    }

    public void execute(TaskId id, TaskDefinition definition) {
        repository.inTransaction(() -> {
            Task current = repository.findTask(id);
            if (current == null) return null;
            long displayOrder = current.displayOrder;
            TaskSlot slot = definition.primarySlot();
            if (current.slot != slot) {
                List<Task> reordered = ordering.moveToEndOfSlot(repository.allTasks(), id,
                        slot, definition.title);
                for (Task task : reordered) {
                    if (task.id.equals(id)) displayOrder = task.displayOrder;
                    else repository.updateTask(task);
                }
            }
            LocalDate nextDue = current.nextDueOn;
            if (current.recurrence != definition.recurrence
                    || current.weekdayMask != definition.weekdayMask) {
                LocalDate today = clock == null ? current.nextDueOn : clock.today();
                if (today != null)
                    nextDue = ScheduleCalculator.firstDue(definition.recurrence,
                            definition.weekdayMask, today);
            }
            repository.updateTask(current.editDefinition(definition, displayOrder, nextDue));
            syncTemplates(id, definition.steps);
            return null;
        });
    }

    public void execute(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                        int intervalDays, int weekdayMask, List<String> stepTexts,
                        boolean ongoing, String condition) {
        if (ongoing) {
            repository.inTransaction(() -> {
                legacyUpdate(id, title, slot, recurrence, intervalDays, weekdayMask,
                        stepTexts, condition);
                return null;
            });
            return;
        }
        Map<Integer, TaskStepTemplate> current = new HashMap<>();
        for (TaskStepTemplate value : repository.templates(id)) current.put(value.position, value);
        List<TaskStepDefinition> steps = new ArrayList<>();
        for (String text : stepTexts) if (text != null && !text.trim().isEmpty()) {
            TaskStepTemplate old = current.get(steps.size());
            steps.add(new TaskStepDefinition(old == null ? null : old.id, steps.size(), text,
                    0, StepAmount.none(), ""));
        }
        execute(id, new TaskDefinition(title, null, slot, recurrence, intervalDays,
                weekdayMask, recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit,
                TaskBoundKind.FOREVER, null, null, null, null, "", steps));
    }

    private void legacyUpdate(TaskId id, String title, TaskSlot slot, Recurrence recurrence,
                              int intervalDays, int weekdayMask, List<String> stepTexts,
                              String condition) {
        Task current = repository.findTask(id);
        if (current == null) return;
        repository.updateTask(current.editDefinition(title, slot, recurrence, intervalDays,
                weekdayMask, true, condition, current.displayOrder));
        repository.deleteTemplates(id);
        List<TaskStepTemplate> values = new ArrayList<>();
        for (String text : stepTexts) if (text != null && !text.trim().isEmpty())
            values.add(new TaskStepTemplate(ids.nextId(), id, values.size(), text));
        repository.insertTemplates(values);
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
                    step.weekdayMask, step.amount, step.note));
        }
        for (TaskStepTemplate old : existing)
            if (!retained.contains(old.id)) repository.deleteTemplate(old.id);
        repository.insertTemplates(updated);
    }
}
