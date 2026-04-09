package com.autosecretary.features.task.application.callchain;

/**
 * Placeholder call-chain catalog for the task feature slice.
 *
 * <p>Call Chain A: Daily Planning
 * <pre>
 * CompletionTrackingGateway.readCompletionHistory()
 *   -> TaskSlotAssignmentApi.assignSlotsFromCompletionTracking(...)
 *   -> TaskScoringApi.computeEffectiveScores(...)
 *   -> TaskPlanningApi.createSlotBoundPlan(...)
 *   -> PlanWriteGateway.saveDraftPlan(...)
 * </pre>
 *
 * <p>Call Chain B: Incremental Replan after Completion Event
 * <pre>
 * CompletionEventIngestGateway.onTaskCompleted(...)
 *   -> CompletionTrackingGateway.appendCompletion(...)
 *   -> TaskSlotAssignmentApi.assignSlotsFromCompletionTracking(...)
 *   -> TaskPlanningApi.createSlotBoundPlan(...)
 * </pre>
 */
public interface TaskPlanningCallChains {

    /** Placeholder signature for documenting daily planning orchestration. */
    void runDailyPlanningChain();

    /** Placeholder signature for documenting event-driven replanning orchestration. */
    void runCompletionDrivenReplanChain();
}
