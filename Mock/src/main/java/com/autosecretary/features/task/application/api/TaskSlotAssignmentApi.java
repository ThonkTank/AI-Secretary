package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskNode;
import com.autosecretary.features.task.domain.model.TimeSlot;
import java.util.List;
import java.util.Map;

/**
 * Capability placeholder: dynamic time-slot assignment driven by completion tracking.
 *
 * <p>Planned behavior:
 * <ul>
 *   <li>Completely decouple slot assignment from score computation.</li>
 *   <li>Use completion telemetry/history to infer best slot per task.</li>
 *   <li>Support re-assignment as completion behavior changes over time.</li>
 * </ul>
 */
public interface TaskSlotAssignmentApi {

    /**
     * Assigns each task to one of the canonical slots.
     *
     * @param tasks all tasks to assign
     * @return mapping taskId -> slot
     */
    Map<String, TimeSlot> assignSlotsFromCompletionTracking(List<TaskNode> tasks);
}
