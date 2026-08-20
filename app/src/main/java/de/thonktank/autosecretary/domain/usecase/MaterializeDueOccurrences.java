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
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Materializes the current calendar plan and rolls unfinished work into today.
 * There is at most one open occurrence for a task/time slot; historical missed
 * occurrences retain their reward ledger but are never shown as active work.
 */
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
            LocalDate today = clock.today();
            List<Task> active = repository.activeTasks();
            List<TaskId> taskIds = new ArrayList<>();
            for (Task task : active) taskIds.add(task.id);
            Map<TaskId, List<TaskStepTemplate>> templates = groupTemplates(
                    repository.templatesFor(taskIds));
            List<Occurrence> allOccurrences = repository.allOccurrences();
            Map<TaskId, List<Occurrence>> occurrences = groupOccurrences(allOccurrences);
            List<String> occurrenceIds = new ArrayList<>();
            for (Occurrence occurrence : allOccurrences) occurrenceIds.add(occurrence.id);
            Map<String, List<OccurrenceStep>> steps = groupSteps(
                    repository.occurrenceStepsFor(occurrenceIds));
            Map<TaskSlot, Integer> globalNextOrders = nextOrders(allOccurrences);
            boolean changed = false;
            for (Task task : active) {
                changed |= prepareTask(task, today, occurrences.getOrDefault(task.id,
                        Collections.emptyList()), templates.get(task.id), globalNextOrders, steps);
            }
            return changed;
        });
    }

    private boolean prepareTask(Task task, LocalDate today, List<Occurrence> history,
                                List<TaskStepTemplate> templates,
                                Map<TaskSlot, Integer> globalNextOrders,
                                Map<String, List<OccurrenceStep>> stepsByOccurrence) {
        List<Occurrence> values = new ArrayList<>(history);
        Map<TaskSlot, Occurrence> open = latestOpen(values);
        Map<TaskSlot, List<OccurrenceStep>> carry = new HashMap<>();
        boolean changed = false;

        // A still-open previous-day occurrence becomes historical. Its unfinished
        // work is the only part that may enter today's active instance.
        for (Map.Entry<TaskSlot, Occurrence> entry : new ArrayList<>(open.entrySet())) {
            Occurrence occurrence = entry.getValue();
            if (!occurrence.scheduledOn.isBefore(today)) continue;
            List<OccurrenceStep> existingSteps = stepsByOccurrence.getOrDefault(occurrence.id,
                    Collections.emptyList());
            List<OccurrenceStep> unfinished = unfinished(existingSteps);
            if (!unfinished.isEmpty() || existingSteps.isEmpty())
                carry.put(entry.getKey(), unfinished);
            repository.updateOccurrence(occurrence.missed());
            open.remove(entry.getKey());
            changed = true;
        }

        // A partially harvested occurrence is closed, but its undone steps are
        // still a catch-up obligation on the next calendar day.
        for (TaskSlot slot : slotsWithCarryCandidates(values, open.keySet(), today)) {
            if (carry.containsKey(slot) || open.containsKey(slot)) continue;
            Occurrence latest = latest(values, slot);
            if (latest == null || !latest.scheduledOn.isBefore(today)) continue;
            List<OccurrenceStep> unfinished = unfinished(stepsByOccurrence.getOrDefault(
                    latest.id, Collections.emptyList()));
            if (!unfinished.isEmpty()) carry.put(slot, unfinished);
        }

        Planned planned = plannedThroughToday(task, today, values, templates);
        changed |= planned.nextDueChanged || !planned.stepsBySlot.isEmpty();

        Set<TaskSlot> targetSlots = new LinkedHashSet<>();
        List<TaskSlot> configuredSlots = task.recurrence == Recurrence.ONCE
                ? Collections.singletonList(task.slot) : TimeOfDay.slots(task.timeOfDayMask);
        for (TaskSlot slot : configuredSlots)
            if (carry.containsKey(slot) || planned.stepsBySlot.containsKey(slot))
                targetSlots.add(slot);
        targetSlots.addAll(carry.keySet());
        targetSlots.addAll(planned.stepsBySlot.keySet());
        for (Map.Entry<TaskSlot, List<TaskStepTemplate>> entry : planned.stepsBySlot.entrySet()) {
            if (entry.getValue() == null) targetSlots.add(entry.getKey());
        }

        for (TaskSlot slot : targetSlots) {
            Occurrence existing = open.get(slot);
            if (existing != null) continue;
            List<OccurrenceStep> steps = new ArrayList<>();
            Set<String> sourceIds = new HashSet<>();
            List<OccurrenceStep> carried = carry.get(slot);
            if (carried != null) {
                for (OccurrenceStep step : carried) {
                    steps.add(copyStep(step, ids.nextId(), "pending:" + task.id.value));
                    if (step.sourceTemplateId != null) sourceIds.add(step.sourceTemplateId);
                }
            }
            List<TaskStepTemplate> fresh = planned.stepsBySlot.get(slot);
            if (fresh != null) for (TaskStepTemplate template : fresh) {
                if (sourceIds.contains(template.id)) continue;
                steps.add(snapshot(template, ids.nextId(), "pending:" + task.id.value));
                sourceIds.add(template.id);
            }
            // A task without steps still needs a visible active occurrence.
            if (steps.isEmpty() && carried == null && fresh == null
                    && !planned.stepsBySlot.containsKey(slot)) continue;
            int order = carried != null && !carried.isEmpty()
                    ? carryOrder(values, slot)
                    : globalNextOrders.getOrDefault(slot, 0) + 1;
            Occurrence occurrence = new Occurrence(ids.nextId(), task.id, today, slot,
                    OccurrenceState.OPEN, order, null);
            repository.insertOccurrence(occurrence);
            globalNextOrders.put(slot, Math.max(globalNextOrders.getOrDefault(slot, 0), order));
            if (!steps.isEmpty()) {
                List<OccurrenceStep> positioned = new ArrayList<>();
                for (int index = 0; index < steps.size(); index++) {
                    OccurrenceStep step = steps.get(index);
                    positioned.add(new OccurrenceStep(step.id, occurrence.id, index, step.text,
                            step.done, step.amount, step.note,
                            step.repetitionProgress == null ? Collections.emptyList()
                                    : step.repetitionProgress.actualRepetitions,
                            step.sourceTemplateId, step.comboOwnerId));
                }
                repository.insertOccurrenceSteps(positioned);
            }
            changed = true;
        }

        if (planned.nextDueChanged || planned.materializedCount > 0) {
            repository.updateTask(task.afterPlanning(planned.nextDue, planned.materializedCount));
            changed = true;
        } else if (task.recurrence == Recurrence.ONCE && open.isEmpty()) {
            // A one-off item with no unfinished work has been missed permanently.
            repository.updateTask(task.withOccurrenceState(true, null, task.lastScheduledOn,
                    task.lastCompletedOn, task.hasCompletedOccurrence));
            changed = true;
        }
        return changed;
    }

    private Planned plannedThroughToday(Task task, LocalDate today, List<Occurrence> history,
                                        List<TaskStepTemplate> templates) {
        Map<TaskSlot, List<TaskStepTemplate>> result = new LinkedHashMap<>();
        LocalDate cursor = task.planningCursor();
        int materialized = 0;
        if (task.boundKind == TaskBoundKind.N_TIMES
                && (task.remainingCount == null || task.remainingCount <= 0))
            return new Planned(result, null, 0, !same(task.planningCursor(), null));
        Set<String> existingDates = new HashSet<>();
        for (Occurrence occurrence : history)
            existingDates.add(key(occurrence.taskId, occurrence.scheduledOn, occurrence.slot));
        List<TaskSlot> slots = task.recurrence == Recurrence.ONCE
                ? Collections.singletonList(task.slot) : TimeOfDay.slots(task.timeOfDayMask);
        while (cursor != null && !cursor.isAfter(today) && canPlan(task, cursor)
                && (task.boundKind != TaskBoundKind.N_TIMES
                || materialized < (task.remainingCount == null ? 0 : task.remainingCount))) {
            for (TaskSlot slot : slots) {
                String occurrenceKey = key(task.id, cursor, slot);
                boolean alreadyMaterialized = existingDates.contains(occurrenceKey);
                if (!alreadyMaterialized && allowed(task, materialized)) {
                    List<TaskStepTemplate> applicable = applicable(templates, cursor);
                    List<TaskStepTemplate> selected = result.computeIfAbsent(slot,
                            ignored -> new ArrayList<>());
                    Set<String> seen = templateIds(selected);
                    for (TaskStepTemplate template : applicable)
                        if (seen.add(template.id)) selected.add(template);
                    if (applicable.isEmpty() && selected.isEmpty()) result.put(slot, null);
                    materialized++;
                }
            }
            LocalDate next = ScheduleCalculator.nextDue(task, cursor);
            if (next == null || (task.boundKind == TaskBoundKind.N_TIMES
                    && materialized >= (task.remainingCount == null ? 0 : task.remainingCount))) {
                cursor = null;
                break;
            }
            cursor = next;
        }
        if (task.recurrence == Recurrence.ONCE && task.planningCursor() != null
                && task.planningCursor().isAfter(today)) {
            cursor = task.planningCursor();
        }
        if (cursor != null && !ScheduleCalculator.withinBound(task, cursor)) cursor = null;
        return new Planned(result, cursor, materialized, !same(cursor, task.planningCursor()));
    }

    private boolean canPlan(Task task, LocalDate date) {
        return ScheduleCalculator.withinBound(task, date)
                && (task.boundKind != TaskBoundKind.N_TIMES
                || task.remainingCount != null && task.remainingCount > 0);
    }

    private boolean allowed(Task task, int alreadyPlanned) {
        return task.boundKind != TaskBoundKind.N_TIMES
                || task.remainingCount != null && alreadyPlanned < task.remainingCount;
    }

    private static List<TaskStepTemplate> applicable(List<TaskStepTemplate> templates,
                                                      LocalDate date) {
        if (templates == null) return Collections.emptyList();
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate template : templates)
            if (ScheduleCalculator.appliesOn(template.weekdayMask, date)) result.add(template);
        return result;
    }

    private static Set<String> templateIds(List<TaskStepTemplate> values) {
        Set<String> result = new HashSet<>();
        for (TaskStepTemplate value : values) result.add(value.id);
        return result;
    }

    private OccurrenceStep copyStep(OccurrenceStep step, String id, String comboOwner) {
        return new OccurrenceStep(id, "pending", 0, step.text, false, step.amount, step.note,
                step.repetitionProgress == null ? Collections.emptyList()
                        : step.repetitionProgress.actualRepetitions,
                step.sourceTemplateId,
                step.comboOwnerId == null ? comboOwner : step.comboOwnerId);
    }

    private OccurrenceStep snapshot(TaskStepTemplate template, String id, String comboOwner) {
        return new OccurrenceStep(id, "pending", 0, template.text, false, template.amount,
                template.note, Collections.emptyList(), template.id,
                "step:" + template.id);
    }

    private static List<OccurrenceStep> unfinished(List<OccurrenceStep> steps) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep step : steps) if (!step.done) result.add(step);
        return result;
    }

    private static Map<TaskSlot, Occurrence> latestOpen(List<Occurrence> values) {
        Map<TaskSlot, Occurrence> result = new HashMap<>();
        for (Occurrence value : values) if (value.state == OccurrenceState.OPEN) {
            Occurrence current = result.get(value.slot);
            if (current == null || value.scheduledOn.isAfter(current.scheduledOn))
                result.put(value.slot, value);
        }
        return result;
    }

    private static Occurrence latest(List<Occurrence> values, TaskSlot slot) {
        return values.stream().filter(value -> value.slot == slot)
                .max(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                        .thenComparing(value -> value.state == OccurrenceState.OPEN ? 1 : 0))
                .orElse(null);
    }

    private static Set<TaskSlot> slotsWithCarryCandidates(List<Occurrence> values,
                                                           Set<TaskSlot> openSlots,
                                                           LocalDate today) {
        Set<TaskSlot> result = new HashSet<>();
        for (Occurrence value : values)
            if (!openSlots.contains(value.slot) && value.scheduledOn.isBefore(today))
                result.add(value.slot);
        return result;
    }

    private static Map<TaskSlot, Integer> nextOrders(List<Occurrence> values) {
        Map<TaskSlot, Integer> result = new HashMap<>();
        for (Occurrence value : values) if (value.state == OccurrenceState.OPEN)
            result.put(value.slot, Math.max(result.getOrDefault(value.slot, 0), value.sortOrder));
        return result;
    }

    private static int carryOrder(List<Occurrence> values, TaskSlot slot) {
        Occurrence value = latest(values, slot);
        return value == null ? 0 : value.sortOrder;
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

    private static String key(TaskId taskId, LocalDate date, TaskSlot slot) {
        return taskId.value + '|' + date + '|' + slot;
    }

    private static boolean same(LocalDate left, LocalDate right) {
        return left == null ? right == null : left.equals(right);
    }

    private static final class Planned {
        final Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot;
        final LocalDate nextDue;
        final int materializedCount;
        final boolean nextDueChanged;

        Planned(Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot, LocalDate nextDue,
                int materializedCount, boolean nextDueChanged) {
            this.stepsBySlot = stepsBySlot;
            this.nextDue = nextDue;
            this.materializedCount = materializedCount;
            this.nextDueChanged = nextDueChanged;
        }
    }
}
