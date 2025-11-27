package com.secretary.features.dreams.data.repository

import com.secretary.features.dreams.data.dao.MilestoneDao
import com.secretary.features.dreams.data.entity.MilestoneEntity
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository implementation for Milestone operations.
 * Dream-to-Task Feature - Data Layer
 *
 * Implements MilestoneRepository interface using Room DAO.
 * Handles entity-to-model mapping for Clean Architecture compliance.
 */
class MilestoneRepositoryImpl(
    private val milestoneDao: MilestoneDao
) : MilestoneRepository {

    override suspend fun insert(milestone: Milestone): Long {
        return milestoneDao.insertMilestone(milestone.toEntity())
    }

    override suspend fun update(milestone: Milestone) {
        milestoneDao.updateMilestone(milestone.toEntity())
    }

    override suspend fun delete(milestoneId: Long) {
        val entity = milestoneDao.getMilestoneById(milestoneId)
        entity?.let { milestoneDao.deleteMilestone(it) }
    }

    override suspend fun getById(milestoneId: Long): Milestone? {
        return milestoneDao.getMilestoneById(milestoneId)?.toDomain()
    }

    override suspend fun getByDreamId(dreamId: Long): List<Milestone> {
        return milestoneDao.getMilestonesByDream(dreamId).map { it.toDomain() }
    }

    override suspend fun updateXP(milestoneId: Long, xpToAdd: Long) {
        val milestone = milestoneDao.getMilestoneById(milestoneId) ?: return
        val updatedMilestone = milestone.copy(
            currentXp = milestone.currentXp + xpToAdd
        )
        milestoneDao.updateMilestone(updatedMilestone)
    }

    override suspend fun complete(milestoneId: Long, completedAt: Long) {
        val milestone = milestoneDao.getMilestoneById(milestoneId) ?: return
        val updatedMilestone = milestone.copy(
            status = MilestoneEntity.Companion.MilestoneStatus.COMPLETED,
            completedAt = completedAt
        )
        milestoneDao.updateMilestone(updatedMilestone)
    }

    override fun observeByDreamId(dreamId: Long): Flow<List<Milestone>> {
        // Note: Room doesn't support Flow in current implementation
        // This is a simple implementation that emits once
        // In a real app, you'd use Room's Flow support: @Query returning Flow<List<MilestoneEntity>>
        return flow {
            emit(milestoneDao.getMilestonesByDream(dreamId).map { it.toDomain() })
        }
    }
}

// ========== Mapping Extensions ==========

/**
 * Convert Milestone domain model to MilestoneEntity.
 */
private fun Milestone.toEntity() = MilestoneEntity(
    milestoneId = id,
    dreamId = dreamId,
    title = title,
    description = description,
    targetXp = targetXp,
    currentXp = currentXp,
    status = status.value,
    createdAt = createdAt,
    completedAt = completedAt,
    orderIndex = orderIndex
)

/**
 * Convert MilestoneEntity to Milestone domain model.
 */
private fun MilestoneEntity.toDomain() = Milestone(
    id = milestoneId,
    dreamId = dreamId,
    title = title,
    description = description,
    targetXp = targetXp,
    currentXp = currentXp,
    status = Milestone.Status.fromInt(status),
    createdAt = createdAt,
    completedAt = completedAt,
    orderIndex = orderIndex
)
