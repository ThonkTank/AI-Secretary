package com.autosecretary.features.task.data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Factory for creating {@link TaskPrefSlot} instances with sensible defaults.
 */
public final class TaskPrefSlotFactory {

    public static final LocalTime DEFAULT_START_TIME = LocalTime.of(6, 0);
    public static final LocalTime DEFAULT_END_TIME = LocalTime.of(21, 0);

    private TaskPrefSlotFactory() {
    }

    public static TaskPrefSlot create(String taskId, Set<DayOfWeek> days, LocalTime start) {
        TaskPrefSlot slot = new TaskPrefSlot();
        slot.taskId = taskId;
        slot.days = EnumSet.copyOf(days);
        slot.start = start;
        return slot;
    }

    /** Creates a pref slot for all 7 days of the week starting at 06:00. */
    public static TaskPrefSlot createDefault(String taskId) {
        return create(taskId, EnumSet.allOf(DayOfWeek.class), DEFAULT_START_TIME);
    }
}
