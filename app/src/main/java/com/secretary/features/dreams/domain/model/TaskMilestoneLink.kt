package com.secretary.features.dreams.domain.model

/**
 * TaskMilestoneLink domain model.
 * Dream-to-Task Feature - Domain Layer
 *
 * Represents a link between a task and a milestone.
 * Allows tasks to contribute XP to multiple milestones.
 */
data class TaskMilestoneLink(
    val taskId: Long,
    val milestoneId: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * String representation showing task and milestone IDs
     */
    override fun toString(): String = "Task $taskId → Milestone $milestoneId"
}
