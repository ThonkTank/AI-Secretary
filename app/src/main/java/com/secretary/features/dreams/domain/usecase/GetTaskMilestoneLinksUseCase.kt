package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.TaskMilestoneLink
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository

/**
 * Use case for retrieving task-milestone links.
 * Dream-to-Task Feature - Domain Layer
 *
 * Queries the relationship between tasks and milestones.
 * Returns Result<List<TaskMilestoneLink>> for error handling.
 */
class GetTaskMilestoneLinksUseCase(
    private val repository: TaskMilestoneLinkRepository
) {
    /**
     * Get all milestone links for a specific task.
     *
     * @param taskId ID of the task
     * @return Result<List<TaskMilestoneLink>> containing links or error
     */
    suspend fun getLinksForTask(taskId: Long): Result<List<TaskMilestoneLink>> {
        return try {
            if (taskId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid task ID: $taskId"))
            }

            val links = repository.getLinksForTask(taskId)
            Result.success(links)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all task links for a specific milestone.
     *
     * @param milestoneId ID of the milestone
     * @return Result<List<TaskMilestoneLink>> containing links or error
     */
    suspend fun getLinksForMilestone(milestoneId: Long): Result<List<TaskMilestoneLink>> {
        return try {
            if (milestoneId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid milestone ID: $milestoneId"))
            }

            val links = repository.getLinksForMilestone(milestoneId)
            Result.success(links)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get count of milestones linked to a task.
     * Useful for XP calculation.
     *
     * @param taskId ID of the task
     * @return Result<Int> containing milestone count or error
     */
    suspend fun getMilestoneCountForTask(taskId: Long): Result<Int> {
        return try {
            if (taskId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid task ID: $taskId"))
            }

            val count = repository.getMilestoneCountForTask(taskId)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
