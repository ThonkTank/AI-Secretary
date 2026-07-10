package com.autosecretary.features.task.ui.edit.internal;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bidirectional mapper between {@link Task} (data layer) and {@link TaskEditState} (UI edit model).
 * Handles null safety and default values during conversion. All methods are static — no instance state.
 */
public final class TaskEditStateMapper {

    private TaskEditStateMapper() {}

    /**
     * Safely copies a day set, returning an empty set if the source is null or empty.
     * Guards against the empty non-EnumSet case: EnumSet.copyOf(Collection) throws
     * IllegalArgumentException when the collection is empty and not already an EnumSet.
     */
    private static Set<DayOfWeek> copyDaysOrEmpty(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return EnumSet.noneOf(DayOfWeek.class);
        return EnumSet.copyOf(days);
    }

    /**
     * Ensures a list is initialized, returning an empty list if null.
     */
    private static <T> List<T> ensureNotNull(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    /**
     * Maps a persisted {@link Task} to a flat {@link TaskEditState} for form editing.
     * Copies all user-editable fields plus scheduler-managed state (periodCompletions,
     * periodStart, carryoverDebt) that must survive the edit round-trip unchanged.
     */
    public static TaskEditState fromTask(Task task) {
        if (task == null) throw new IllegalArgumentException("task cannot be null");
        TaskEditState state = new TaskEditState();
        state.id = task.core.id;
        state.title = task.core.title;
        state.description = task.core.description;
        state.priority = task.core.priority;
        state.schedulingType = task.core.schedulingType;
        state.parentTaskId = task.parents.isEmpty() ? null : task.parents.get(0).parent;
        state.budgetRequiredCents = task.core.budgetRequiredCents;
        state.budgetAccountId = task.core.budgetAccountId;
        state.budgetCategoryId = task.core.budgetCategoryId;

        state.startDate = task.core.startDate;
        state.deadline = task.core.deadline;
        state.fixedDate = task.core.fixedDate;
        state.fixedStart = task.core.fixedStart;
        state.fixedEnd = task.core.fixedEnd;
        state.fixedDuration = task.core.fixedDuration;
        state.closeOnMiss = task.core.closeOnMiss;
        state.minDuration = task.core.minDuration;
        state.maxDuration = task.core.maxDuration;
        state.cooldown = task.core.cooldown;
        state.adaptive = task.core.adaptive;

        state.reps = task.core.repetition.reps;
        state.perPeriod = task.core.repetition.perPeriod;
        state.periodUnit = task.core.repetition.periodUnit;
        state.periodCompletions = task.core.repetition.periodCompletions;
        state.periodStart = task.core.repetition.periodStart;
        state.completeFirst = task.core.repetition.completeFirst;
        state.carryoverDebt = task.core.repetition.carryoverDebt;

        state.unit = Objects.requireNonNullElse(task.core.progress.unit, TaskEditDefaults.UNIT);
        state.target = task.core.progress.target;
        state.current = task.core.progress.current;
        state.resetPerRep = task.core.progress.resetPerRep;
        state.minPerRep = task.core.progress.minPerRep;
        state.maxPerRep = task.core.progress.maxPerRep;

        state.prefSlots = new ArrayList<>();
        for (TaskPrefSlot prefSlot : ensureNotNull(task.prefSlots)) {
            PrefSlotEditState slotState = new PrefSlotEditState();
            slotState.id = prefSlot.id;
            slotState.start = prefSlot.start;
            slotState.days = copyDaysOrEmpty(prefSlot.days);
            state.prefSlots.add(slotState);
        }
        return state;
    }

    /**
     * Applies the edited state back onto a {@link Task} for DB persistence.
     * Uses {@code baseTask} (the original loaded task) to preserve fields not in the edit
     * state (e.g. slots, parents, prerequisites). If {@code baseTask} is null, creates a new Task.
     */
    public static Task toTask(TaskEditState state, Task baseTask) {
        if (state == null) throw new IllegalArgumentException("state cannot be null");
        Task task = baseTask != null ? baseTask : new Task();
        task.core = task.core != null ? task.core : new TaskCore();

        if (state.id != null) task.core.id = state.id;
        task.core.title = state.title;
        task.core.description = state.description;
        task.core.priority = state.priority;
        task.core.schedulingType = state.schedulingType;
        task.core.budgetRequiredCents = state.budgetRequiredCents;
        task.core.budgetAccountId = state.budgetAccountId;
        task.core.budgetCategoryId = state.budgetCategoryId;

        task.core.startDate = state.startDate;
        task.core.deadline = state.deadline;
        task.core.fixedDate = state.fixedDate;
        task.core.fixedStart = state.fixedStart;
        task.core.fixedEnd = state.fixedEnd;
        task.core.fixedDuration = state.fixedDuration;
        task.core.closeOnMiss = state.closeOnMiss;
        task.core.minDuration = state.minDuration;
        task.core.maxDuration = state.maxDuration;
        task.core.cooldown = state.cooldown;
        task.core.adaptive = state.adaptive;

        // TaskCore initializes Repetition and Progress as non-null field initializers,
        // so direct field access below is safe even for a freshly constructed TaskCore.
        task.core.repetition.reps = state.reps;
        task.core.repetition.perPeriod = state.perPeriod;
        task.core.repetition.periodUnit = state.periodUnit;
        task.core.repetition.periodCompletions = state.periodCompletions;
        task.core.repetition.periodStart = state.periodStart;
        task.core.repetition.completeFirst = state.completeFirst;
        task.core.repetition.carryoverDebt = state.carryoverDebt;

        task.core.progress.unit = state.unit;
        task.core.progress.target = state.target;
        task.core.progress.current = state.current;
        task.core.progress.resetPerRep = state.resetPerRep;
        task.core.progress.minPerRep = state.minPerRep;
        task.core.progress.maxPerRep = state.maxPerRep;

        task.prefSlots = new ArrayList<>();
        for (PrefSlotEditState prefSlotState : state.prefSlots) {
            TaskPrefSlot prefSlot = new TaskPrefSlot();
            if (prefSlotState.id != null) prefSlot.id = prefSlotState.id;
            prefSlot.taskId = task.core.id;
            prefSlot.start = prefSlotState.start;
            prefSlot.days = copyDaysOrEmpty(prefSlotState.days);
            task.prefSlots.add(prefSlot);
        }

        // Set parent relation from edit state. Replaces whatever the base task had.
        task.parents = new ArrayList<>();
        if (state.parentTaskId != null) {
            task.parents.add(new TaskRelation(state.parentTaskId, task.core.id));
        }

        // Task carries several Room @Relation lists (slots, prerequisites, plannedMeals).
        // If the base task was constructed without loading all relations these lists may be null,
        // so ensure they are non-null lists to avoid NPEs in downstream callers.
        task.slots = ensureNotNull(task.slots);
        task.prerequisites = ensureNotNull(task.prerequisites);
        task.plannedMeals = ensureNotNull(task.plannedMeals);
        return task;
    }
}
