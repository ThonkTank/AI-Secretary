package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MaterializeDueOccurrences {
    private final TaskRepository repository;
    private final Clock clock;
    private final IdGenerator ids;

    public MaterializeDueOccurrences(TaskRepository repository, Clock clock, IdGenerator ids) {
        this.repository = repository;
        this.clock = clock;
        this.ids = ids;
    }

    public boolean execute() {
        return repository.inTransaction(() -> {
            boolean changed = false;
            List<Occurrence> occurrences = repository.allOccurrences();
            Set<String> existing = new HashSet<>();
            for (Occurrence occurrence : occurrences) existing.add(key(occurrence.taskId,
                    occurrence.scheduledOn, occurrence.slot));
            Map<TaskSlot, Integer> nextOrder = nextOrders(occurrences);
            List<Task> due = new ArrayList<>();
            for (Task task : repository.activeTasks())
                if (ScheduleCalculator.isDue(task, clock.today())
                        && ScheduleCalculator.withinBound(task, task.nextDueOn)) due.add(task);
            List<TaskId> idsToLoad = new ArrayList<>();
            for (Task task : due) idsToLoad.add(task.id);
            Map<TaskId, List<TaskStepTemplate>> templates = groupTemplates(
                    repository.templatesFor(idsToLoad));

            for (Task task : due) {
                List<TaskSlot> slots = task.recurrence == Recurrence.ONCE
                        ? Collections.singletonList(task.slot) : TimeOfDay.slots(task.timeOfDayMask);
                int allowed = slots.size();
                if (task.boundKind == TaskBoundKind.N_TIMES)
                    allowed = Math.min(allowed, task.remainingCount == null ? 0 : task.remainingCount);
                int created = 0;
                for (TaskSlot slot : slots) {
                    if (created >= allowed) break;
                    String occurrenceKey = key(task.id, task.nextDueOn, slot);
                    if (existing.contains(occurrenceKey)) continue;
                    int order = nextOrder.getOrDefault(slot, 0) + 1;
                    nextOrder.put(slot, order);
                    Occurrence occurrence = new Occurrence(ids.nextId(), task.id, task.nextDueOn,
                            slot, OccurrenceState.OPEN, order, null);
                    repository.insertOccurrence(occurrence);
                    existing.add(occurrenceKey);
                    repository.insertOccurrenceSteps(snapshotSteps(occurrence,
                            templates.get(task.id)));
                    created++;
                    changed = true;
                }
                if (created > 0 && task.boundKind == TaskBoundKind.N_TIMES)
                    repository.updateTask(task.afterMaterializing(created));
            }
            return changed;
        });
    }

    private List<OccurrenceStep> snapshotSteps(Occurrence occurrence,
                                               List<TaskStepTemplate> templates) {
        List<OccurrenceStep> result = new ArrayList<>();
        if (templates == null) return result;
        for (TaskStepTemplate step : templates) {
            if (!ScheduleCalculator.appliesOn(step.weekdayMask, occurrence.scheduledOn)) continue;
            result.add(new OccurrenceStep(ids.nextId(), occurrence.id, result.size(), step.text,
                    false, step.amount, step.note, Collections.emptyList(),
                    step.id, ComboProgress.stepOwner(step.id)));
        }
        return result;
    }

    private static Map<TaskId, List<TaskStepTemplate>> groupTemplates(
            List<TaskStepTemplate> values) {
        Map<TaskId, List<TaskStepTemplate>> result = new HashMap<>();
        for (TaskStepTemplate step : values)
            result.computeIfAbsent(step.taskId, ignored -> new ArrayList<>()).add(step);
        return result;
    }

    private static Map<TaskSlot, Integer> nextOrders(List<Occurrence> occurrences) {
        Map<TaskSlot, Integer> result = new HashMap<>();
        for (Occurrence occurrence : occurrences) {
            if (occurrence.state != OccurrenceState.OPEN) continue;
            result.put(occurrence.slot,
                    Math.max(result.getOrDefault(occurrence.slot, 0), occurrence.sortOrder));
        }
        return result;
    }

    private static String key(TaskId taskId, java.time.LocalDate date, TaskSlot slot) {
        return taskId.value + '|' + date + '|' + slot;
    }
}
