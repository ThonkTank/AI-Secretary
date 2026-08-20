package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure calendar planning. It does not inspect or mutate open occurrence state. */
final class DueDatePlanner {
    Plan throughToday(Task task, LocalDate today, List<Occurrence> history,
                      List<TaskStepTemplate> templates) {
        Map<TaskSlot, List<TaskStepTemplate>> result = new LinkedHashMap<>();
        LocalDate cursor = task.planningCursor();
        int materialized = 0;
        if (task.boundKind == TaskBoundKind.N_TIMES
                && (task.remainingCount == null || task.remainingCount <= 0))
            return new Plan(result, null, 0, !same(task.planningCursor(), null));
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
                && task.planningCursor().isAfter(today))
            cursor = task.planningCursor();
        if (cursor != null && !ScheduleCalculator.withinBound(task, cursor)) cursor = null;
        return new Plan(result, cursor, materialized, !same(cursor, task.planningCursor()));
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

    private static String key(de.thonktank.autosecretary.domain.model.TaskId taskId,
                              LocalDate date, TaskSlot slot) {
        return taskId.value + '|' + date + '|' + slot;
    }

    private static boolean same(LocalDate left, LocalDate right) {
        return left == null ? right == null : left.equals(right);
    }

    static final class Plan {
        final Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot;
        final LocalDate nextDue;
        final int materializedCount;
        final boolean nextDueChanged;

        Plan(Map<TaskSlot, List<TaskStepTemplate>> stepsBySlot, LocalDate nextDue,
             int materializedCount, boolean nextDueChanged) {
            this.stepsBySlot = stepsBySlot;
            this.nextDue = nextDue;
            this.materializedCount = materializedCount;
            this.nextDueChanged = nextDueChanged;
        }
    }
}
