package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.BucketPlan;
import com.autosecretary.features.task.domain.model.TaskNode;
import com.autosecretary.features.task.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Assembles concrete daily bucket plans from pre-computed signals.
 *
 * <h2>Who calls this</h2>
 * Nightly orchestrator and daytime completion-replan path.
 *
 * <h2>How this API uses other APIs</h2>
 * Consumes outputs from {@link TaskScoringApi}, {@link TaskSlotAssignmentApi}, and capacity gateway.
 *
 * <h2>Why these parameters</h2>
 * <ul>
 *   <li>{@code planningDay}: part of persistence key and deterministic plan identity.</li>
 *   <li>{@code tasks}: provides durations and hierarchy context.</li>
 *   <li>{@code scores}: direct rank order, avoids planner recomputing scoring logic.</li>
 *   <li>{@code availableMinutesPerBucket}: direct capacity budget, avoids planner recomputing calendar math.</li>
 * </ul>
 */
public interface TaskPlanningApi {

    /**
     * Creates a bucket plan by descending score until bucket minutes are exhausted.
     * Tasks may appear in multiple buckets if uncertainty policy allows.
     */
    BucketPlan createBucketPlan(LocalDate planningDay,
                                List<TaskNode> tasks,
                                Map<String, Double> scores,
                                Map<TimeSlot, Integer> availableMinutesPerBucket);

    /**
     * Removes stale later-bucket duplicates after completion and refills newly freed capacity.
     *
     * @param existingPlan persisted current-day plan snapshot (typically from PlanReadGateway)
     * @param completedTaskId task completed earlier in the day
     */
    BucketPlan replanAfterCompletion(BucketPlan existingPlan, String completedTaskId);
}
