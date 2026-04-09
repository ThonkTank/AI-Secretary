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
 * <p>Call Chain A: Task Creation + Definition Update
 * <pre>
 * TaskCreationApi.createTask(draft, idempotencyKey)
 *   -> TaskWriteGateway.createTaskDefinition(definition, idempotencyKey)
 *   -> TaskWriteGateway.upsertTaskRecurrence(taskId, recurrenceRule, idempotencyKey)
 *   -> TaskReadGateway.readTaskById(taskId)
 * </pre>
 *
 * <p>Call Chain B: Nightly Daily Planning
 * <pre>
 * PlanningTriggerGateway.onNightlyTrigger()
 *   -> TaskReadGateway.readPlanningCandidates(day)
 *   -> BucketWindowConfigGateway.readBucketWindowsForDay(day)
 *   -> CalendarAvailabilityGateway.readBlockedIntervals(day)
 *   -> BucketCapacityGateway.computeAvailableMinutes(day, windows, blocked)
 *   -> CompletionTrackingGateway.readCompletionHistory(from, to)
 *   -> TaskSlotAssignmentApi.assignEligibleSlots(tasks)
 *   -> TaskScoringApi.computeDailyScores(tasks, day)
 *   -> TaskPlanningApi.createBucketPlan(day, tasks, scores, freeMinutes)
 *   -> PlanWriteGateway.saveDraftPlan(day, plan, idempotencyKey)
 *   -> TaskWriteGateway.upsertPlannedForDay(taskId, day)  // optional
 * </pre>
 * <p>Call Chain C: Completion-driven Cleanup + Refill
 * <pre>
 * CompletionEventIngestGateway.onTaskCompleted(taskId, finishedAtUtc, idempotencyKey)
 *   -> TaskWriteGateway.markCompleted(taskId, finishedAtUtc, idempotencyKey)
 *   -> CompletionTrackingGateway.appendCompletion(event)
 *   -> CompletionTrackingGateway.readCompletionStats(taskId)
 *   -> TaskWriteGateway.updateCompletionStreakSnapshot(taskId, stats, idempotencyKey)
 *   -> PlanReadGateway.readPlanForDay(day) // includes current revision
 *   -> TaskPlanningApi.replanAfterCompletion(plan, taskId)
 *   -> PlanWriteGateway.overwritePlan(day, updatedPlan, expectedRevision, idempotencyKey)
 * </pre>
 */
public interface TaskPlanningCallChains {

    /** Nightly orchestration entry point. */
    void runNightlyPlanningChain();

    /** Task creation/update orchestration entry point. */
    void runTaskCreationAndDefinitionUpdateChain();

    /** Daytime completion-driven cleanup/refill entry point. */
    void runCompletionDrivenCleanupAndRefillChain();
}
