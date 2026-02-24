package com.autosecretary.features.task.ui.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

public class PrefSlotEditState {
    public String id;
    public String taskId;
    public Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    public LocalTime start;
}
