package com.autosecretary.features.task.ui.edit.internal.mapper;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.ui.edit.TaskEditPresenter;
import com.autosecretary.features.task.ui.edit.state.PrefSlotEditState;
import com.autosecretary.features.task.ui.edit.state.TaskEditState;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/**
 * Bidirectional mapper between {@link Task} (data layer) and {@link TaskEditState} (UI edit model).
 * Handles null safety and default values during conversion.
 */
public class TaskEditStateMapper {

    /**
     * Safely copies a day set, returning an empty set if the source is null.
     */
    private Set<DayOfWeek> copyDaysOrEmpty(Set<DayOfWeek> days) {
        return days != null ? EnumSet.copyOf(days) : EnumSet.noneOf(DayOfWeek.class);
    }

    /**
     * Ensures a list is initialized, returning an empty list if null.
     */
    private <T> java.util.List<T> ensureNotNull(java.util.List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    public TaskEditState fromTask(Task task) {
        if (task == null) throw new IllegalArgumentException("task cannot be null");
        TaskEditState state = new TaskEditState();
        state.id = task.core.id;
        state.title = task.core.title;
        state.description = task.core.description;
        state.priority = task.core.priority;
        state.schedulingType = task.core.schedulingType;
        state.goalIcon = TaskEditPresenter.coalesce(task.core.goalIcon, TaskCore.DEFAULT_GOAL_ICON);
        state.goalColorHex = TaskEditPresenter.coalesce(task.core.goalColorHex, TaskCore.DEFAULT_GOAL_COLOR_HEX);
        state.budgetRequiredCents = task.core.budgetRequiredCents;
        state.budgetAccountId = task.core.budgetAccountId;
        state.budgetCategoryId = task.core.budgetCategoryId;

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

        state.unit = task.core.progress.unit;
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

    public Task toTask(TaskEditState state, Task baseTask) {
        if (state == null) throw new IllegalArgumentException("state cannot be null");
        Task task = baseTask != null ? baseTask : new Task();
        task.core = task.core != null ? task.core : new TaskCore();

        if (state.id != null) task.core.id = state.id;
        task.core.title = state.title;
        task.core.description = state.description;
        task.core.priority = state.priority;
        task.core.schedulingType = state.schedulingType;
        task.core.goalIcon = TaskEditPresenter.coalesce(state.goalIcon, TaskCore.DEFAULT_GOAL_ICON);
        task.core.goalColorHex = TaskEditPresenter.coalesce(state.goalColorHex, TaskCore.DEFAULT_GOAL_COLOR_HEX);
        task.core.budgetRequiredCents = state.budgetRequiredCents;
        task.core.budgetAccountId = state.budgetAccountId;
        task.core.budgetCategoryId = state.budgetCategoryId;

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
            prefSlot.id = prefSlotState.id; // New slots keep null IDs until persistence assigns one.
            prefSlot.taskId = task.core.id;
            prefSlot.start = prefSlotState.start;
            prefSlot.days = copyDaysOrEmpty(prefSlotState.days);
            task.prefSlots.add(prefSlot);
        }

        task.slots = ensureNotNull(task.slots);
        task.parents = ensureNotNull(task.parents);
        task.prerequisites = ensureNotNull(task.prerequisites);
        task.plannedMeals = ensureNotNull(task.plannedMeals);
        return task;
    }
}
