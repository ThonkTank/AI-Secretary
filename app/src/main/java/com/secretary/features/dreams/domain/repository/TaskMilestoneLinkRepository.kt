package com.secretary.features.dreams.domain.repository

import com.secretary.features.dreams.domain.model.TaskMilestoneLink

/**
 * Repository interface for TaskMilestoneLink operations.
 * Dream-to-Task Feature - Domain Layer
 *
 * Manages many-to-many relationships between tasks and milestones.
 * Allows tasks to contribute XP to multiple milestones.
 */
interface TaskMilestoneLinkRepository {

    /**
     * Create a link between a task and a milestone.
     * @param taskId The ID of the task
     * @param milestoneId The ID of the milestone
     */
    suspend fun link(taskId: Long, milestoneId: Long)

    /**
     * Remove the link between a task and a milestone.
     * @param taskId The ID of the task
     * @param milestoneId The ID of the milestone
     */
    suspend fun unlink(taskId: Long, milestoneId: Long)

    /**
     * Get all milestone links for a specific task.
     * @param taskId The ID of the task
     * @return List of links for the task
     */
    suspend fun getLinksForTask(taskId: Long): List<TaskMilestoneLink>

    /**
     * Get all task links for a specific milestone.
     * @param milestoneId The ID of the milestone
     * @return List of links for the milestone
     */
    suspend fun getLinksForMilestone(milestoneId: Long): List<TaskMilestoneLink>

    /**
     * Get the count of milestones linked to a task.
     * Used for XP calculation.
     * @param taskId The ID of the task
     * @return Number of milestones linked to the task
     */
    suspend fun getMilestoneCountForTask(taskId: Long): Int
}
