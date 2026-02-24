package com.autosecretary.database.task;

import org.junit.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class TaskPrefSlotFactoryTest {

    @Test
    public void createDefault_initializesTaskIdDaysAndStartTime() {
        String taskId = "task-123";

        TaskPrefSlot slot = TaskPrefSlotFactory.createDefault(taskId);

        assertEquals(taskId, slot.taskId);
        assertEquals(EnumSet.allOf(DayOfWeek.class), slot.days);
        assertEquals(TaskPrefSlotFactory.DEFAULT_START_TIME, slot.start);
    }
}
