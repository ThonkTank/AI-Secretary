package de.thonktank.autosecretary.presentation.alltasks;

import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Flat, stable row projection consumed by the virtualized management list. */
final class AllTasksRow {
    enum Kind { TASK_HEADER, STEP_TARGET, STEP, STEP_ADD, SLOT_HEADER,
        SCHEDULE_TARGET, SCHEDULE, EMPTY }

    final Kind kind;
    final String key;
    final long stableId;
    final AllTasksUiState.TaskItem task;
    final TaskStepTemplate step;
    final AllTasksUiState.ScheduleItem schedule;
    final String taskId;
    final String cardKey;
    final String beforeId;
    final TaskSlot slot;
    final boolean endTarget;
    final EmptyReason emptyReason;

    enum EmptyReason { NO_TASKS, SEARCH, FILTERS, STATUS }

    private AllTasksRow(Kind kind, String key, AllTasksUiState.TaskItem task,
                        TaskStepTemplate step,
                        AllTasksUiState.ScheduleItem schedule, String taskId, String cardKey,
                        String beforeId, TaskSlot slot, boolean endTarget,
                        EmptyReason emptyReason) {
        this.kind = kind;
        this.key = key;
        this.stableId = stableId(key);
        this.task = task;
        this.step = step;
        this.schedule = schedule;
        this.taskId = taskId;
        this.cardKey = cardKey;
        this.beforeId = beforeId;
        this.slot = slot;
        this.endTarget = endTarget;
        this.emptyReason = emptyReason;
    }

    static List<AllTasksRow> project(AllTasksUiState state) {
        if (state.mode == AllTasksUiState.Mode.SORT) return scheduleRows(state);
        return taskRows(state);
    }

    private static List<AllTasksRow> taskRows(AllTasksUiState state) {
        if (state.tasks.isEmpty()) return Collections.singletonList(empty(state));
        List<AllTasksRow> result = new ArrayList<>();
        for (AllTasksUiState.TaskItem item : state.tasks) {
            String taskId = item.task.id.value;
            String cardKey = item.cardKey;
            result.add(new AllTasksRow(Kind.TASK_HEADER, "task:" + cardKey,
                    item, null, null, taskId, cardKey, null, null, false, null));
            if (!item.expanded) continue;
            for (TaskStepTemplate step : item.visibleSteps) {
                if (!item.archived)
                    result.add(target(Kind.STEP_TARGET, taskId, cardKey,
                            step.id, null, false));
                result.add(new AllTasksRow(Kind.STEP, "step:" + cardKey + ':' + step.id,
                        item, step, null, taskId, cardKey, null, null, false, null));
            }
            if (!item.archived)
                result.add(target(Kind.STEP_TARGET, taskId, cardKey, null, null, true));
            result.add(new AllTasksRow(Kind.STEP_ADD, "add:" + cardKey,
                    item, null, null, taskId, cardKey,
                    null, null, true, null));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<AllTasksRow> scheduleRows(AllTasksUiState state) {
        if (state.schedule.isEmpty()) return Collections.singletonList(empty(state));
        List<AllTasksRow> result = new ArrayList<>();
        for (TaskSlot slot : TaskSlot.values()) {
            if (!state.slots.isEmpty() && !state.slots.contains(slot)) continue;
            result.add(new AllTasksRow(Kind.SLOT_HEADER, "slot:" + slot.name(),
                    null, null, null, null, null, null, slot, false, null));
            for (AllTasksUiState.ScheduleItem item : state.schedule) {
                if (item.slot != slot) continue;
                result.add(target(Kind.SCHEDULE_TARGET, null, null, item.id, slot, false));
                result.add(new AllTasksRow(Kind.SCHEDULE, "schedule:" + item.id,
                        null, null, item, item.taskId, null, null, slot, false, null));
            }
            result.add(target(Kind.SCHEDULE_TARGET, null, null, null, slot, true));
        }
        return Collections.unmodifiableList(result);
    }

    private static AllTasksRow target(Kind kind, String taskId, String cardKey, String beforeId,
                                      TaskSlot slot, boolean end) {
        String owner = taskId == null ? slot.name() : cardKey;
        String before = beforeId == null ? "end" : beforeId;
        return new AllTasksRow(kind, kind.name() + ':' + owner + ':' + before,
                null, null, null,
                taskId, cardKey, beforeId, slot,
                end, null);
    }

    private static AllTasksRow empty(AllTasksUiState state) {
        EmptyReason reason;
        if (!state.query.trim().isEmpty()) reason = EmptyReason.SEARCH;
        else if (!state.slots.isEmpty() || !state.recurrences.isEmpty() || state.weekday != 0)
            reason = EmptyReason.FILTERS;
        else reason = EmptyReason.STATUS;
        return new AllTasksRow(Kind.EMPTY, "empty:" + reason, null,
                null, null, null, null, null, null, false, reason);
    }

    private static long stableId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
