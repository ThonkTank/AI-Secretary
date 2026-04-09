package com.autosecretary.features.task.ui.edit.state;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Mutable UI POJO for a single preferred slot's editable fields (days and start time).
 * <p>
 * A "preferred slot" (PrefSlot) is a day-of-week and start-time pattern (e.g., "Monday and Wednesday
 * at 10:00 AM") that guides the scheduler when generating candidate time slots for this task.
 * See CLAUDE.md glossary entry "PrefSlot" for details.
 */
public class PrefSlotEditState {
    public String id;
    public Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);  // days of week on which this slot is preferred
    public LocalTime start;  // preferred start time of day
}
