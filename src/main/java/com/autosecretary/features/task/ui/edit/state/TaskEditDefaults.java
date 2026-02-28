package com.autosecretary.features.task.ui.edit.state;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

/**
 * Canonical default values for a new task's editable fields.
 * <p>
 * Both {@link TaskEditState} (field initializers) and
 * {@link com.autosecretary.features.task.ui.edit.FormInput FormInput}
 * (form-input fallbacks) reference these constants so that "default for a new task" and
 * "fallback when the user enters invalid input" are always the same value.
 */
public final class TaskEditDefaults {

    private TaskEditDefaults() {}

    // Scheduling & priority
    public static final Priority PRIORITY = Priority.MEDIUM;
    public static final TaskCore.SchedulingType SCHEDULING_TYPE = TaskCore.SchedulingType.TASK;

    // Goal presentation
    // These delegate to TaskCore domain defaults to ensure UI and domain are always in sync.
    public static final String GOAL_ICON = TaskCore.DEFAULT_GOAL_ICON;
    public static final String GOAL_COLOR_HEX = TaskCore.DEFAULT_GOAL_COLOR_HEX;

    // Scheduling constraints
    public static final int MIN_DURATION = 5;  // minutes; minimum duration for a slot
    public static final int MAX_DURATION = 10;  // minutes; maximum duration for a slot
    public static final int COOLDOWN = 1;  // days; minimum gap between successive slots
    public static final boolean CLOSE_ON_MISS = true;  // close task after deadline if not completed

    // Repetition — how often does the task repeat
    public static final int REPETITION_REPS = 1;  // 1 repetition by default (one-off task)
    public static final int REPETITION_PER_PERIOD = 1;  // 1 time per period (once daily/weekly/monthly)
    public static final Period REPETITION_PERIOD_UNIT = Period.DAY;  // day/week/month

    // Progress tracking (optional) — for numeric progress tracking (e.g., reading chapters)
    // Empty unit string means "no progress tracking"; other fields are ignored if unit is empty
    public static final String UNIT = "";  // label for the progress unit (e.g., "pages", "chapters")
    public static final int TARGET = 0;  // goal value (0 = no target)
    public static final int CURRENT = 0;  // initial current value
    public static final int MIN_PER_REP = 0;  // minimum progress per repetition
    public static final int MAX_PER_REP = 0;  // maximum progress per repetition
}
