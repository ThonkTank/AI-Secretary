package com.autosecretary.features.task.ui.edit.state;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable UI POJO holding all editable fields for {@link com.autosecretary.features.task.ui.edit.TaskEditDialog TaskEditDialog}.
 * Not persisted -- exists only during active editing sessions.
 */
public class TaskEditState {
    public String id;
    public String title;
    public String description;
    public String goalIcon = TaskEditDefaults.GOAL_ICON;
    public String goalColorHex = TaskEditDefaults.GOAL_COLOR_HEX;
    public Priority priority = TaskEditDefaults.PRIORITY;
    public TaskCore.SchedulingType schedulingType = TaskEditDefaults.SCHEDULING_TYPE;
    public Integer budgetRequiredCents;
    public String budgetAccountId;
    public String budgetCategoryId;

    public LocalDate deadline;
    public LocalDate fixedDate;
    public LocalTime fixedStart;
    public LocalTime fixedEnd;
    public Integer fixedDuration;
    public boolean closeOnMiss = true;
    public int minDuration = TaskEditDefaults.MIN_DURATION;
    public int maxDuration = TaskEditDefaults.MAX_DURATION;
    public int cooldown = TaskEditDefaults.COOLDOWN;
    public boolean adaptive;

    public int reps;
    public int perPeriod = TaskEditDefaults.REPETITION_PER_PERIOD;
    public Period periodUnit = TaskEditDefaults.REPETITION_PERIOD_UNIT;
    public int periodCompletions;
    public LocalDate periodStart;
    public boolean completeFirst;
    public int carryoverDebt;

    public String unit;
    public int target;
    public int current;
    public boolean resetPerRep;
    public int minPerRep;
    public int maxPerRep;

    public List<PrefSlotEditState> prefSlots = new ArrayList<>();
}
