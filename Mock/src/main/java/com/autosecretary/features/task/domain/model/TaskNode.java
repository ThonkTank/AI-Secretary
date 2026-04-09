package com.autosecretary.features.task.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Planning-facing task projection for bucket scheduling.
 *
 * <h2>Who consumes this type</h2>
 * - {@code TaskScoringApi}
 * - {@code TaskSlotAssignmentApi}
 * - {@code TaskPlanningApi}
 *
 * <h2>Why this shape</h2>
 * Exposes exactly the fields those consumers need, so they do not have to fetch
 * extra storage models or derive core planning values themselves.
 */
public interface TaskNode {

    /** Stable identity used across scoring, assignment, plan rows, and completion events. */
    String taskId();

    /** Parent relationship for optional co-planning / inheritance logic. */
    String parentTaskId();

    /** Child references for hierarchy-aware policies. */
    List<String> childTaskIds();

    /** Base contribution into scoring pipeline. */
    double baseScore();

    /** Planned effort used by planner to decrement bucket capacity. */
    int effortMinutes();

    /** Optional soft hints for slot-assignment inference (can be empty). */
    Set<TimeSlot> preferredSlots();
}
