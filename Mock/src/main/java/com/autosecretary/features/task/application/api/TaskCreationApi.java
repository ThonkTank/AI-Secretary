package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskDefinition;

/**
 * Creates task definitions with required naming, optional parent hierarchy, and recurrence policy.
 *
 * <h2>Who calls this</h2>
 * Task creation/editing flows in UI/application layer.
 *
 * <h2>Why this exists</h2>
 * Makes task-definition lifecycle explicit instead of coupling creation semantics to planning gateways.
 */
public interface TaskCreationApi {

    /**
     * Validates and persists one task definition, then returns created task id.
     */
    String createTask(TaskDefinition draft, String idempotencyKey);
}
