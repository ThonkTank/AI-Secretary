package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.TaskCompletionStats;
import com.autosecretary.features.task.domain.model.TaskDefinition;
import com.autosecretary.features.task.domain.model.TaskRecurrenceRule;
import java.time.Instant;
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
     * Persists a newly created task definition (name required, parent optional).
     */
    String createTaskDefinition(TaskDefinition definition, String idempotencyKey);

    /**
     * Upserts recurrence policy for a task (including non-repeating to repeating transitions).
     */
    void upsertTaskRecurrence(String taskId, TaskRecurrenceRule recurrenceRule, String idempotencyKey);

    /**
     * Marks task as completed at runtime checkoff time.
     */
    void markCompleted(String taskId, Instant finishedAtUtc, String idempotencyKey);

    /**
     * Persists optional "planned for day" marker used by task list projections or dedup logic.
     */
    void upsertPlannedForDay(String taskId, LocalDate day);

    /**
     * Persists derived completion streak/counter snapshot used for low-latency scoring reads.
     */
    void updateCompletionStreakSnapshot(String taskId, TaskCompletionStats stats, String idempotencyKey);
}
