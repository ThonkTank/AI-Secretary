package com.secretary.features.dreams.data.repository

import com.secretary.features.dreams.data.dao.TaskMilestoneJunctionDao
import com.secretary.features.dreams.data.entity.TaskMilestoneJunction
import com.secretary.features.dreams.domain.model.TaskMilestoneLink
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository

/**
 * Repository implementation for TaskMilestoneLink operations.
 * Dream-to-Task Feature - Data Layer
 *
 * Implements TaskMilestoneLinkRepository interface using Room DAO.
 * Manages many-to-many relationships between tasks and milestones.
 */
class TaskMilestoneLinkRepositoryImpl(
    private val junctionDao: TaskMilestoneJunctionDao
) : TaskMilestoneLinkRepository {

    override suspend fun link(taskId: Long, milestoneId: Long) {
        val junction = TaskMilestoneJunction(
            taskId = taskId,
            milestoneId = milestoneId,
            createdAt = System.currentTimeMillis()
        )
        junctionDao.insertLink(junction)
    }

    override suspend fun unlink(taskId: Long, milestoneId: Long) {
        val junction = TaskMilestoneJunction(
            taskId = taskId,
            milestoneId = milestoneId
        )
        junctionDao.deleteLink(junction)
    }

    override suspend fun getLinksForTask(taskId: Long): List<TaskMilestoneLink> {
        return junctionDao.getLinksForTask(taskId).map { it.toDomain() }
    }

    override suspend fun getLinksForMilestone(milestoneId: Long): List<TaskMilestoneLink> {
        return junctionDao.getLinksForMilestone(milestoneId).map { it.toDomain() }
    }

    override suspend fun getMilestoneCountForTask(taskId: Long): Int {
        return junctionDao.getMilestoneCountForTask(taskId)
    }
}

// ========== Mapping Extensions ==========

/**
 * Convert TaskMilestoneJunction entity to TaskMilestoneLink domain model.
 */
private fun TaskMilestoneJunction.toDomain() = TaskMilestoneLink(
    taskId = taskId,
    milestoneId = milestoneId,
    createdAt = createdAt
)
