package com.autosecretary.features.task.data;

import java.time.LocalDate;

/**
 * Write boundary for task-state mutations caused by planning lifecycle events.
 *
 * <h2>Who calls this</h2>
 * Completion-driven path and optional nightly bookkeeping steps.
 *
 * <h2>Why this exists</h2>
 * Makes task write paths explicit (separate from completion telemetry and plan persistence).
 */
public interface TaskWriteGateway {

    /**
     * Marks task as completed at runtime checkoff time.
     */
    void markCompleted(String taskId, String finishedAtIso);

    /**
     * Persists optional "planned for day" marker used by task list projections or dedup logic.
     */
    void upsertPlannedForDay(String taskId, LocalDate day);
}
