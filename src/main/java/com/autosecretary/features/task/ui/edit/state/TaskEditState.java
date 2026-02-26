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
    public String goalIcon = TaskCore.DEFAULT_GOAL_ICON;
    public String goalColorHex = TaskCore.DEFAULT_GOAL_COLOR_HEX;
    public Priority priority = Priority.MEDIUM;

    public LocalDate deadline;
    public boolean closeOnMiss = true;
    public int minDuration = 5;
    public int maxDuration = 10;
    public int cooldown = 1;
    public boolean adaptive;
    public boolean isFixedAppointment;
    public LocalDate fixedDate;
    public LocalTime fixedStart;
    public int fixedDurationMinutes;

    public int reps;
    public int perPeriod = 1;
    public Period periodUnit = Period.DAY;
    public int periodCompletions;
    public LocalDate periodStart;

    public String unit;
    public int target;
    public int current;
    public boolean resetPerRep;
    public int minPerRep;
    public int maxPerRep;

    public List<PrefSlotEditState> prefSlots = new ArrayList<>();
}
