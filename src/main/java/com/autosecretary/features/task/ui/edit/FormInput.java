package com.autosecretary.features.task.ui.edit;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.ui.edit.state.TaskEditDefaults;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Raw form field values collected from the dialog UI on save.
 * Produced by {@link com.autosecretary.features.task.ui.edit.internal.editor.TaskEditFormInputReader#read()},
 * consumed by {@link TaskEditPresenter#applyForm(FormInput)}.
 * When adding a new form field, update both classes accordingly.
 */
public class FormInput {
    public String title;
    public String description;
    public Priority priority = TaskEditDefaults.PRIORITY;
    public String goalIcon = TaskEditDefaults.GOAL_ICON;
    public String goalColorHex = TaskEditDefaults.GOAL_COLOR_HEX;
    public TaskCore.SchedulingType schedulingType = TaskEditDefaults.SCHEDULING_TYPE;
    // Fixed-date fields for SchedulingType.TERMIN — shown in the UI only when that type is selected.
    // Written by TaskEditFormInputReader when the views contain parseable values.
    public LocalDate fixedDate;
    public LocalTime fixedStart;
    public LocalTime fixedEnd;
    public Integer fixedDuration;
    // Budget integration (optional) — if set, task completion auto-books an expense
    public Integer budgetRequiredCents;  // required amount in cents; null/0 means no budget link
    public String budgetAccountId;  // which budget account to charge against
    public String budgetCategoryId;  // which budget category to use; see CLAUDE.md "Task → Budget integration"

    public boolean closeOnMiss;
    public int minDuration = TaskEditDefaults.MIN_DURATION;  // minutes
    public int maxDuration = TaskEditDefaults.MAX_DURATION;  // minutes
    public int cooldown = TaskEditDefaults.COOLDOWN;  // days (minimum gap between slots)
    public boolean adaptive;  // if true, preferred times auto-adjust from real completion times

    public boolean repetitionEnabled;
    public int reps = TaskEditDefaults.REPETITION_REPS;
    public int perPeriod = TaskEditDefaults.REPETITION_PER_PERIOD;
    public Period periodUnit = TaskEditDefaults.REPETITION_PERIOD_UNIT;
    public boolean completeFirst;

    public boolean progressEnabled;
    public String unit;
    public int target = TaskEditDefaults.TARGET;
    public int current = TaskEditDefaults.CURRENT;
    public boolean resetPerRep;
    public int minPerRep = TaskEditDefaults.MIN_PER_REP;
    public int maxPerRep = TaskEditDefaults.MAX_PER_REP;
}
