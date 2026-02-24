package com.autosecretary.database.task;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

public final class TaskPrefSlotFactory {

    public static final LocalTime DEFAULT_START_TIME = LocalTime.of(6, 0);

    private TaskPrefSlotFactory() {
    }

    public static TaskPrefSlot createDefault(String taskId) {
        TaskPrefSlot slot = new TaskPrefSlot();
        slot.taskId = taskId;
        slot.days = EnumSet.allOf(DayOfWeek.class);
        slot.start = DEFAULT_START_TIME;
        return slot;
    }
}
