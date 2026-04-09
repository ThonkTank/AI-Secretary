package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskNode;
import com.autosecretary.features.task.domain.model.TimeSlot;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves bucket eligibility (possibly multiple buckets per task).
 *
 * <h2>Who calls this</h2>
 * Nightly orchestrator before plan assembly.
 *
 * <h2>How output is consumed</h2>
 * Planner intersects eligibility with remaining capacity and score order.
 *
 * <h2>Why this output shape</h2>
 * Returning {@code taskId -> Set<TimeSlot>} avoids forcing callers to re-interpret raw telemetry.
 * The planner receives exactly the eligibility contract it needs.
 *
 * <h2>External dependencies this type will need (in implementation)</h2>
 * Completion telemetry and optional task metadata/preferences.
 */
public interface TaskSlotAssignmentApi {

    /**
     * @return map taskId -> set of eligible buckets (empty set = currently ineligible)
     */
    Map<String, Set<TimeSlot>> assignEligibleSlots(List<TaskNode> tasks);
}
