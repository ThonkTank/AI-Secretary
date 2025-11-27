package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository

/**
 * Use case for linking a task to one or more milestones.
 * Dream-to-Task Feature - Domain Layer
 *
 * Manages task-milestone relationships for XP distribution.
 * When a task is completed, XP is awarded to ALL linked milestones.
 *
 * Returns Result<Unit> for error handling.
 */
class LinkTaskToMilestonesUseCase(
    private val repository: TaskMilestoneLinkRepository
) {
    /**
     * Link a task to multiple milestones.
     * Removes existing links and creates new ones atomically.
     *
     * @param taskId ID of the task to link
     * @param milestoneIds List of milestone IDs to link to
     * @return Result<Unit> indicating success or error
     */
    suspend operator fun invoke(
        taskId: Long,
        milestoneIds: List<Long>
    ): Result<Unit> {
        return try {
            // Validate inputs
            if (taskId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid task ID: $taskId"))
            }

            if (milestoneIds.any { it <= 0 }) {
                return Result.failure(IllegalArgumentException("Invalid milestone IDs in list"))
            }

            // Get existing links
            val existingLinks = repository.getLinksForTask(taskId)
            val existingMilestoneIds = existingLinks.map { it.milestoneId }.toSet()
            val newMilestoneIds = milestoneIds.toSet()

            // Remove links that are no longer needed
            val toRemove = existingMilestoneIds - newMilestoneIds
            for (milestoneId in toRemove) {
                repository.unlink(taskId, milestoneId)
            }

            // Add new links
            val toAdd = newMilestoneIds - existingMilestoneIds
            for (milestoneId in toAdd) {
                repository.link(taskId, milestoneId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
