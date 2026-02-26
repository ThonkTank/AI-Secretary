package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;
import java.util.List;

/** Public domain contract for multi-day task slot generation. */
public interface TaskSlotGenerator {
    void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive, LocalDate endExclusive, TaskPlanningState state);
    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state);
    TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay, int days, TaskPlanningState state);
    void recordScheduledSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state);
}
