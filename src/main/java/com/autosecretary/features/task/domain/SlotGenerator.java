package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stable domain entry point for schedule generation.
 */
public interface SlotGenerator {

    interface PlanningState {}

    PlanningState createPlanningState();

    void recordPreservedSlots(List<Task> tasks,
                              LocalDate startInclusive,
                              LocalDate endExclusive,
                              PlanningState state);

    void generateSlotsForDay(List<Task> tasks,
                             LocalDateTime windowStart,
                             LocalDateTime windowEnd,
                             PlanningState state);

    void recordScheduledSlotsForDay(List<Task> tasks,
                                    LocalDate day,
                                    PlanningState state);
}
