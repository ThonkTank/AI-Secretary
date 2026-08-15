package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
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

    public void execute() {
        repository.inTransaction(() -> {
            Set<TaskId> alreadyOpen = new HashSet<>();
            List<Occurrence> open = repository.openOccurrences();
            for (Occurrence occurrence : open) alreadyOpen.add(occurrence.taskId);
            List<Task> due = new ArrayList<>();
            Map<TaskId, Task> active = new HashMap<>();
            for (Task task : repository.activeTasks()) {
                active.put(task.id, task);
                if (!alreadyOpen.contains(task.id) && ScheduleCalculator.isDue(task, clock.today()))
                    due.add(task);
            }
            List<TaskId> dueIds = new ArrayList<>();
            for (Task task : due) dueIds.add(task.id);
            Map<TaskId, List<TaskStepTemplate>> templates = groupTemplates(repository.templatesFor(dueIds));
            Map<TaskSlot, Integer> nextOrder = nextOrders(open, active);

            for (Task task : due) {
                int order = nextOrder.getOrDefault(task.slot, 0) + 1;
                nextOrder.put(task.slot, order);
                Occurrence occurrence = new Occurrence(ids.nextId(), task.id, task.nextDueOn,
                        OccurrenceState.OPEN, order, null);
                repository.insertOccurrence(occurrence);
                List<OccurrenceStep> steps = new ArrayList<>();
                List<TaskStepTemplate> values = templates.get(task.id);
                if (values != null)
                    for (TaskStepTemplate step : values)
                        steps.add(new OccurrenceStep(ids.nextId(), occurrence.id,
                                step.position, step.text, false));
                repository.insertOccurrenceSteps(steps);
            }
        });
    }

    private static Map<TaskId, List<TaskStepTemplate>> groupTemplates(List<TaskStepTemplate> values) {
        Map<TaskId, List<TaskStepTemplate>> result = new HashMap<>();
        for (TaskStepTemplate step : values)
            result.computeIfAbsent(step.taskId, ignored -> new ArrayList<>()).add(step);
        return result;
    }

    private static Map<TaskSlot, Integer> nextOrders(List<Occurrence> open, Map<TaskId, Task> tasks) {
        Map<TaskSlot, Integer> result = new HashMap<>();
        for (Occurrence occurrence : open) {
            Task task = tasks.get(occurrence.taskId);
            if (task != null)
                result.put(task.slot, Math.max(result.getOrDefault(task.slot, 0), occurrence.sortOrder));
        }
        return result;
    }
}
