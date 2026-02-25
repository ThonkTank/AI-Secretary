package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public domain contract for task schedule generation.
 */
public interface TaskSlotGenerator {

    TaskPlanningState createPlanningState();

    void recordPreservedSlots(List<Task> tasks,
                              LocalDate startInclusive,
                              LocalDate endExclusive,
                              TaskPlanningState state);

    void generateSlotsForDay(List<Task> tasks,
                             LocalDateTime windowStart,
                             LocalDateTime windowEnd,
                             TaskPlanningState state);

    void recordScheduledSlotsForDay(List<Task> tasks,
                                    LocalDate day,
                                    TaskPlanningState state);
}
