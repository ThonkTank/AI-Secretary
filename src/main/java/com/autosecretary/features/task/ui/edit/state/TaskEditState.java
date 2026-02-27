package com.autosecretary.features.task.ui.edit.state;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable UI POJO for an active editing session in
 * {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog}.
 * Not persisted — exists only during active editing sessions.
 * <p>
 * Most fields are directly user-editable via form widgets. Three fields —
 * {@code periodCompletions}, {@code periodStart}, and {@code carryoverDebt} — are
 * not exposed in any UI widget. They are preserved for round-trip fidelity: when the
 * user saves without changing the repetition period, the mapper writes these values back
 * unchanged so the scheduler's active period progress counters are not silently reset.
 */
public class TaskEditState {
    // Basic metadata
    public String id;
    public String title;
    public String description;
    public String goalIcon = TaskEditDefaults.GOAL_ICON;
    public String goalColorHex = TaskEditDefaults.GOAL_COLOR_HEX;

    // Scheduling & priority
    public Priority priority = TaskEditDefaults.PRIORITY;
    public TaskCore.SchedulingType schedulingType = TaskEditDefaults.SCHEDULING_TYPE;
    public LocalDate deadline;
    public LocalDate fixedDate;
    public LocalTime fixedStart;
    public LocalTime fixedEnd;
    public Integer fixedDuration;
    public boolean closeOnMiss = TaskEditDefaults.CLOSE_ON_MISS;
    public int minDuration = TaskEditDefaults.MIN_DURATION;
    public int maxDuration = TaskEditDefaults.MAX_DURATION;
    public int cooldown = TaskEditDefaults.COOLDOWN;
    public boolean adaptive;

    // Budget integration
    public Integer budgetRequiredCents;
    public String budgetAccountId;
    public String budgetCategoryId;

    // Repetition
    public int reps = TaskEditDefaults.REPETITION_REPS;
    public int perPeriod = TaskEditDefaults.REPETITION_PER_PERIOD;
    public Period periodUnit = TaskEditDefaults.REPETITION_PERIOD_UNIT;
    public int periodCompletions;
    public LocalDate periodStart;
    public boolean completeFirst;
    public int carryoverDebt;

    // Progress tracking
    public String unit = TaskEditDefaults.UNIT;
    public int target = TaskEditDefaults.TARGET;
    public int current = TaskEditDefaults.CURRENT;
    public boolean resetPerRep;
    public int minPerRep = TaskEditDefaults.MIN_PER_REP;
    public int maxPerRep = TaskEditDefaults.MAX_PER_REP;

    // Related state
    public List<PrefSlotEditState> prefSlots = new ArrayList<>();
}
