package com.autosecretary.features.task.ui.state;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** Mutable UI POJO for a single preferred slot's editable fields (days and start time). */
public class PrefSlotEditState {
    public String id;
    public String taskId;
    public Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    public LocalTime start;
}
