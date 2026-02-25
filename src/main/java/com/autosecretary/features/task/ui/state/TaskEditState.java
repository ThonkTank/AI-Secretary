package com.autosecretary.features.task.ui.state;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;

import java.time.LocalDate;
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
    public Priority priority = Priority.MEDIUM;

    public LocalDate deadline;
    public boolean closeOnMiss = true;
    public int minDuration = 5;
    public int maxDuration = 10;
    public int cooldown = 1;
    public boolean adaptive;

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
