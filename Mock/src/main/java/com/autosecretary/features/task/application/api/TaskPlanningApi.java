package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskNode;
import java.util.List;

/**
 * Capability placeholder: score-ordered planning with slot-capacity constraints.
 *
 * <p>Planned behavior:
 * <ul>
 *   <li>Evaluate all tasks in one flat pass (no recursive planning-by-layer).</li>
 *   <li>Within each slot, order by effective score descending.</li>
 *   <li>Only plan tasks that fit slot duration capacity.</li>
 *   <li>If a child task is selected and parent is not planned yet, co-plan parent immediately.</li>
 * </ul>
 */
public interface TaskPlanningApi {

    /**
     * Generates a concrete plan from previously scored and assigned tasks.
     *
     * @param tasks tasks including parent/child relationships
     * @return placeholder ordered plan output
     */
    List<String> createSlotBoundPlan(List<TaskNode> tasks);
}
