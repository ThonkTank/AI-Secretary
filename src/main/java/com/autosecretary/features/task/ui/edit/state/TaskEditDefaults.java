package com.autosecretary.features.task.ui.edit.state;

import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

/**
 * Canonical default values for a new task's editable fields.
 * <p>
 * Both {@link TaskEditState} (field initializers) and
 * {@link com.autosecretary.features.task.ui.edit.TaskEditPresenter.InputDefaults InputDefaults}
 * (form-input fallbacks) reference these constants so that "default for a new task" and
 * "fallback when the user enters invalid input" are always the same value.
 */
public final class TaskEditDefaults {

    private TaskEditDefaults() {}

    public static final Priority PRIORITY = Priority.MEDIUM;
    public static final TaskCore.SchedulingType SCHEDULING_TYPE = TaskCore.SchedulingType.TASK;

    public static final String GOAL_ICON = TaskCore.DEFAULT_GOAL_ICON;
    public static final String GOAL_COLOR_HEX = TaskCore.DEFAULT_GOAL_COLOR_HEX;

    public static final int MIN_DURATION = 5;
    public static final int MAX_DURATION = 10;
    public static final int COOLDOWN = 1;

    public static final int REPETITION_REPS = 1;
    public static final int REPETITION_PER_PERIOD = 1;
    public static final Period REPETITION_PERIOD_UNIT = Period.DAY;
}
