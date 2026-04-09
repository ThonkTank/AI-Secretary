package com.autosecretary.features.task.data;

/**
 * Ingest seam for runtime completion events.
 *
 * <h2>Who calls this</h2>
 * Completion/checkoff flow in UI/application layer.
 *
 * <h2>Why this exists</h2>
 * Decouples user interaction flow from replanning internals; caller emits domain event,
 * implementation decides persistence + downstream dynamic replan trigger.
 */
public interface CompletionEventIngestGateway {

    /** Emits a completion event for tracking and potential dynamic bucket cleanup/refill. */
    void onTaskCompleted(String taskId, String finishedAtIso);
}
