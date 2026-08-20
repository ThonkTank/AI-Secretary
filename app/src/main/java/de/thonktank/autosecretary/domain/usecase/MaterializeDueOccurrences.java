package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional orchestration for the current calendar plan.
 *
 * Calendar planning, stale-work rollover and occurrence creation are deliberately delegated
 * to separate components. This class owns only the transaction boundary and task-level order.
 */
public final class MaterializeDueOccurrences {
    private final TaskRepository repository;
    private final Clock clock;
    private final IdGenerator ids;
    private final DueDatePlanner planner = new DueDatePlanner();

    public MaterializeDueOccurrences(TaskRepository repository, Clock clock, IdGenerator ids) {
        this.repository = repository;
        this.clock = clock;
        this.ids = ids;
    }

    public boolean execute() {
        return repository.inTransaction(() -> {
            LocalDate today = clock.today();
            List<Task> active = repository.activeTasks();
            List<TaskId> taskIds = new ArrayList<>();
            for (Task task : active) taskIds.add(task.id);
            Map<TaskId, List<TaskStepTemplate>> templates = groupTemplates(
                    repository.templatesFor(taskIds));
            Map<String, Integer> scheduleRanks = scheduleRanks(
                    repository.scheduleEntriesFor(taskIds));
            List<Occurrence> allOccurrences = repository.allOccurrences();
            Map<TaskId, List<Occurrence>> occurrences = groupOccurrences(allOccurrences);
            List<String> occurrenceIds = new ArrayList<>();
            for (Occurrence occurrence : allOccurrences) occurrenceIds.add(occurrence.id);
            Map<String, List<OccurrenceStep>> steps = groupSteps(
                    repository.occurrenceStepsFor(occurrenceIds));
            Map<TaskSlot, Integer> globalNextOrders = nextOrders(allOccurrences);
            boolean changed = false;
            for (Task task : active)
                changed |= prepareTask(task, today,
                        occurrences.getOrDefault(task.id, Collections.emptyList()),
                        templates.getOrDefault(task.id, Collections.emptyList()),
                        globalNextOrders, steps, scheduleRanks);
            return changed;
        });
    }

    private boolean prepareTask(Task task, LocalDate today, List<Occurrence> history,
                                List<TaskStepTemplate> templates,
                                Map<TaskSlot, Integer> globalNextOrders,
                                Map<String, List<OccurrenceStep>> stepsByOccurrence,
                                Map<String, Integer> scheduleRanks) {
        OccurrenceCarryForward.Result carry = new OccurrenceCarryForward()
                .collect(repository, today, history, stepsByOccurrence);
        DueDatePlanner.Plan planned = planner.throughToday(task, today, history, templates);
        boolean changed = carry.changed;
        changed |= new OccurrenceAssembler(repository, ids).assemble(task, today, history,
                globalNextOrders, carry, planned, scheduleRanks);

        if (planned.nextDueChanged || planned.materializedCount > 0) {
            repository.updateTask(task.afterPlanning(planned.nextDue, planned.materializedCount));
            changed = true;
        } else if (task.recurrence == Recurrence.ONCE && carry.open.isEmpty()) {
            repository.updateTask(task.withOccurrenceState(true, null, task.lastScheduledOn,
                    task.lastCompletedOn, task.hasCompletedOccurrence));
            changed = true;
        }
        return changed;
    }

    private static Map<TaskSlot, Integer> nextOrders(List<Occurrence> values) {
        Map<TaskSlot, Integer> result = new HashMap<>();
        for (Occurrence value : values) if (value.state == de.thonktank.autosecretary.domain.model.OccurrenceState.OPEN)
            result.put(value.slot, Math.max(result.getOrDefault(value.slot, 0), value.sortOrder));
        return result;
    }

    private static Map<TaskId, List<Occurrence>> groupOccurrences(List<Occurrence> values) {
        Map<TaskId, List<Occurrence>> result = new HashMap<>();
        for (Occurrence value : values)
            result.computeIfAbsent(value.taskId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<String, List<OccurrenceStep>> groupSteps(List<OccurrenceStep> values) {
        Map<String, List<OccurrenceStep>> result = new HashMap<>();
        for (OccurrenceStep value : values)
            result.computeIfAbsent(value.occurrenceId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<TaskId, List<TaskStepTemplate>> groupTemplates(
            List<TaskStepTemplate> values) {
        Map<TaskId, List<TaskStepTemplate>> result = new HashMap<>();
        for (TaskStepTemplate value : values)
            result.computeIfAbsent(value.taskId, ignored -> new ArrayList<>()).add(value);
        return result;
    }

    private static Map<String, Integer> scheduleRanks(List<TaskScheduleEntry> values) {
        values.sort(java.util.Comparator
                .comparingInt((TaskScheduleEntry value) -> value.slot.rank)
                .thenComparingLong(value -> value.displayOrder).thenComparing(value -> value.id));
        Map<String, Integer> result = new HashMap<>();
        Map<TaskSlot, Integer> next = new HashMap<>();
        for (TaskScheduleEntry value : values) {
            int rank = next.getOrDefault(value.slot, 0) + 1;
            next.put(value.slot, rank);
            result.put(value.taskId.value + '|' + value.slot.name(), rank);
        }
        return result;
    }
}
