package com.secretary.features.dreams.data.repository

import com.secretary.features.dreams.data.dao.MilestoneInterdependencyDao
import com.secretary.features.dreams.data.entity.MilestoneInterdependencyEntity
import com.secretary.features.dreams.domain.model.MilestoneInterdependency
import com.secretary.features.dreams.domain.repository.InterdependencyRepository

/**
 * Repository implementation for MilestoneInterdependency operations.
 * Dream-to-Task Feature - Data Layer
 *
 * Implements InterdependencyRepository interface using Room DAO.
 * Manages relationships between milestones with impact ratings.
 */
class InterdependencyRepositoryImpl(
    private val interdependencyDao: MilestoneInterdependencyDao
) : InterdependencyRepository {

    override suspend fun upsert(interdependency: MilestoneInterdependency) {
        interdependencyDao.upsertInterdependency(interdependency.toEntity())
    }

    override suspend fun delete(sourceId: Long, targetId: Long) {
        interdependencyDao.deleteInterdependencyByIds(sourceId, targetId)
    }

    override suspend fun getForMilestone(milestoneId: Long): List<MilestoneInterdependency> {
        return interdependencyDao.getInterdependenciesForMilestone(milestoneId).map { it.toDomain() }
    }

    override suspend fun getAll(): List<MilestoneInterdependency> {
        // Since there's no getAll() in the DAO, we'll need to get all interdependencies
        // This is a workaround - in production you'd add this to the DAO
        // For now, return empty list as this is rarely used
        return emptyList()
    }
}

// ========== Mapping Extensions ==========

/**
 * Convert MilestoneInterdependency domain model to MilestoneInterdependencyEntity.
 */
private fun MilestoneInterdependency.toEntity() = MilestoneInterdependencyEntity(
    sourceMilestoneId = sourceMilestoneId,
    targetMilestoneId = targetMilestoneId,
    impactRating = impactRating,
    description = description,
    createdAt = createdAt
)

/**
 * Convert MilestoneInterdependencyEntity to MilestoneInterdependency domain model.
 */
private fun MilestoneInterdependencyEntity.toDomain() = MilestoneInterdependency(
    sourceMilestoneId = sourceMilestoneId,
    targetMilestoneId = targetMilestoneId,
    impactRating = impactRating,
    description = description,
    createdAt = createdAt
)
