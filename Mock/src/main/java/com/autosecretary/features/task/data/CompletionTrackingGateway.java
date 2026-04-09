package com.autosecretary.features.task.data;

import java.time.LocalDate;
import java.util.List;

/**
 * Access contract for completion telemetry.
 *
 * <h2>Who calls this</h2>
 * - Nightly planning (to infer bucket eligibility / behavior patterns)
 * - Completion event path (to append fresh events)
 *
 * <h2>Why this exists</h2>
 * Centralizes completion IO so assignment/scoring/planning callers receive directly usable
 * historical signals without coupling to storage format.
 */
public interface CompletionTrackingGateway {

    /**
     * Reads completion events for a bounded horizon.
     *
     * <p>Why bounded dates: callers can tune recency windows for stable inference cost.
     */
    List<String> readCompletionHistory(LocalDate fromInclusive, LocalDate toInclusive);

    /**
     * Persists one completion event used by later inference and dynamic replan decisions.
     */
    void appendCompletion(String taskId, String finishedAtIso);
}
