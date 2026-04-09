package com.autosecretary.features.task.data;

import java.util.List;

/**
 * Placeholder data capability: completion-tracking IO contract.
 */
public interface CompletionTrackingGateway {

    /**
     * Reads historical completion signals used for dynamic slot assignment.
     *
     * @return placeholder history payload
     */
    List<String> readCompletionHistory();

    /**
     * Persists a new completion event for later assignment inference.
     *
     * @param taskId completed task id
     * @param finishedAtIso timestamp in ISO format
     */
    void appendCompletion(String taskId, String finishedAtIso);
}
