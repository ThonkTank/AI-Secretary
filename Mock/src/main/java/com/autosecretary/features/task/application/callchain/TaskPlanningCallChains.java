package com.autosecretary.features.task.application.callchain;

/**
 * Call-chain catalog for the target architecture.
 *
 * <h2>Who uses this placeholder</h2>
 * Future application service/use-case that orchestrates planning and dynamic replanning.
 *
 * <h2>Why this exists</h2>
 * Keeps orchestration rules explicit before implementation so each gateway/API gets the
 * right request/response shape from the start.
 *
 * <p>Call Chain A: Nightly Daily Planning
 * <pre>
 * PlanningTriggerGateway.onNightlyTrigger()
 *   -> BucketWindowConfigGateway.readBucketWindowsForDay(day)
 *   -> CalendarAvailabilityGateway.readBlockedIntervals(day)
 *   -> BucketCapacityGateway.computeAvailableMinutes(day, windows, blocked)
 *   -> CompletionTrackingGateway.readCompletionHistory(from, to)
 *   -> TaskSlotAssignmentApi.assignEligibleSlots(tasks)
 *   -> TaskScoringApi.computeDailyScores(tasks, day)
 *   -> TaskPlanningApi.createBucketPlan(day, tasks, scores, freeMinutes)
 *   -> PlanWriteGateway.saveDraftPlan(day, plan)
 * </pre>
 *
 * <p>Call Chain B: Completion-driven Cleanup + Refill
 * <pre>
 * CompletionEventIngestGateway.onTaskCompleted(taskId, finishedAt)
 *   -> CompletionTrackingGateway.appendCompletion(taskId, finishedAt)
 *   -> TaskPlanningApi.replanAfterCompletion(plan, taskId)
 *   -> PlanWriteGateway.overwritePlan(day, updatedPlan)
 * </pre>
 */
public interface TaskPlanningCallChains {

    /** Nightly orchestration entry point. */
    void runNightlyPlanningChain();

    /** Daytime completion-driven cleanup/refill entry point. */
    void runCompletionDrivenCleanupAndRefillChain();
}
