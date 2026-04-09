package com.autosecretary.features.task.domain.model;

/**
 * Canonical task-definition contract for creation/update flows.
 */
public record TaskDefinition(String taskId,
                             String name,
                             String parentTaskId,
                             TaskRecurrenceRule recurrenceRule,
                             boolean active) {
}
