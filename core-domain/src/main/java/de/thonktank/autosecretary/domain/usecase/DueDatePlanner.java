package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskSchedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure calendar planning. It does not inspect or mutate open occurrence state. */
final class DueDatePlanner {
    Plan throughToday(Task task, TaskSchedule schedule, LocalDate today, List<Occurrence> history,
                      List<TaskStepTemplate> templates) {
        Map<TaskSlot, List<TaskStepTemplate>> result = new LinkedHashMap<>();
        List<PlannedDue> dues = new ArrayList<>();
        LocalDate cursor = task.planningCursor();
        int materialized = 0;
        if (task.boundKind == TaskBoundKind.N_TIMES
                && (task.remainingCount == null || task.remainingCount <= 0))
            return new Plan(result, dues, null, 0, !same(task.planningCursor(), null));
        Set<String> existingDates = new HashSet<>();
        for (Occurrence occurrence : history)
            existingDates.add(key(occurrence.taskId, occurrence.scheduledOn, occurrence.slot));
        List<TaskSlot> slots = schedule.slots(task.id);
        if (slots.isEmpty())
            throw new IllegalStateException("Active task has no schedule: " + task.id.value);
        LocalDate intervalAnchor = task.cadenceAnchorOn;
        if (!task.archived && !task.conditionDone && hasIntervalCadence(templates)
                && intervalAnchor == null)
            throw new IllegalStateException("Active task with step intervals has no cadence "
                    + "anchor: " + task.id.value);
        while (cursor != null && !cursor.isAfter(today) && canPlan(task, cursor)
                && (task.boundKind != TaskBoundKind.N_TIMES
                || materialized < (task.remainingCount == null ? 0 : task.remainingCount))) {
            for (TaskSlot slot : slots) {
                String occurrenceKey = key(task.id, cursor, slot);
                boolean alreadyMaterialized = existingDates.contains(occurrenceKey);
                if (!alreadyMaterialized && allowed(task, materialized)) {
                    List<TaskStepTemplate> applicable = applicable(templates, cursor,
                            intervalAnchor);
                    dues.add(new PlannedDue(cursor, slot, applicable));
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
                && task.planningCursor().isAfter(today))
            cursor = task.planningCursor();
        if (cursor != null && !ScheduleCalculator.withinBound(task, cursor)) cursor = null;
        return new Plan(result, dues, cursor, materialized,
                !same(cursor, task.planningCursor()));
    }

    private static boolean canPlan(Task task, LocalDate date) {
        return ScheduleCalculator.withinBound(task, date)
                && (task.boundKind != TaskBoundKind.N_TIMES
                || task.remainingCount != null && task.remainingCount > 0);
    }

    private static boolean allowed(Task task, int alreadyPlanned) {
        return task.boundKind != TaskBoundKind.N_TIMES
                || task.remainingCount != null && alreadyPlanned < task.remainingCount;
    }

    private static List<TaskStepTemplate> applicable(List<TaskStepTemplate> templates,
                                                      LocalDate date, LocalDate intervalAnchor) {
        if (templates == null) return Collections.emptyList();
        List<TaskStepTemplate> result = new ArrayList<>();
        for (TaskStepTemplate template : templates)
            if (appliesOn(template, date, intervalAnchor)) result.add(template);
        return result;
    }

    private static boolean appliesOn(TaskStepTemplate template, LocalDate date,
                                     LocalDate intervalAnchor) {
        if (!ScheduleCalculator.appliesOn(template.weekdayMask, date)) return false;
        if (template.intervalDays == 0) return true;
        long elapsed = ChronoUnit.DAYS.between(intervalAnchor, date);
        return elapsed >= 0 && elapsed % template.intervalDays == 0;
    }

    private static boolean hasIntervalCadence(List<TaskStepTemplate> templates) {
        if (templates == null) return false;
        for (TaskStepTemplate template : templates)
            if (template.intervalDays > 0) return true;
        return false;
    }

    private static Set<String> templateIds(List<TaskStepTemplate> values) {
        Set<String> result = new HashSet<>();
        for (TaskStepTemplate value : values) result.add(value.id);
        return result;
    }

    private static String key(de.thonktank.autosecretary.domain.model.TaskId taskId,
                              LocalDate date, TaskSlot slot) {
        return taskId.value + '|' + date + '|' + slot;
    }

    private static boolean same(LocalDate left, LocalDate right) {
        return left == null ? right == null : left.equals(right);
    }

    static final class Plan {
        final Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot;
        final List<PlannedDue> dues;
        final LocalDate nextDue;
        final int materializedCount;
        final boolean nextDueChanged;

        Plan(Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot, List<PlannedDue> dues,
             LocalDate nextDue,
             int materializedCount, boolean nextDueChanged) {
            this.stepsBySlot = stepsBySlot;
            this.dues = Collections.unmodifiableList(new ArrayList<>(dues));
            this.nextDue = nextDue;
            this.materializedCount = materializedCount;
            this.nextDueChanged = nextDueChanged;
        }
    }

    static final class PlannedDue {
        final LocalDate scheduledOn;
        final TaskSlot slot;
        final List<TaskStepTemplate> templates;

        PlannedDue(LocalDate scheduledOn, TaskSlot slot, List<TaskStepTemplate> templates) {
            this.scheduledOn = scheduledOn;
            this.slot = slot;
            this.templates = Collections.unmodifiableList(new ArrayList<>(templates));
        }
    }
}
