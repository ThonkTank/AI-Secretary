package com.secretary.features.dreams.data.repository

import com.secretary.features.dreams.data.dao.DreamDao
import com.secretary.features.dreams.data.entity.DreamEntity
import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.repository.DreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository implementation for Dream operations.
 * Dream-to-Task Feature - Data Layer
 *
 * Implements DreamRepository interface using Room DAO.
 * Handles entity-to-model mapping for Clean Architecture compliance.
 */
class DreamRepositoryImpl(
    private val dreamDao: DreamDao
) : DreamRepository {

    override suspend fun insert(dream: Dream): Long {
        return dreamDao.insertDream(dream.toEntity())
    }

    override suspend fun update(dream: Dream) {
        dreamDao.updateDream(dream.toEntity())
    }

    override suspend fun delete(dreamId: Long) {
        val entity = dreamDao.getDreamById(dreamId)
        entity?.let { dreamDao.deleteDream(it) }
    }

    override suspend fun getById(dreamId: Long): Dream? {
        return dreamDao.getDreamById(dreamId)?.toDomain()
    }

    override suspend fun getAll(): List<Dream> {
        return dreamDao.getAllDreams().map { it.toDomain() }
    }

    override suspend fun getAllActive(): List<Dream> {
        return dreamDao.getActiveDreams().map { it.toDomain() }
    }

    override fun observeAll(): Flow<List<Dream>> {
        // Note: Room doesn't support Flow in current implementation
        // This is a simple implementation that emits once
        // In a real app, you'd use Room's Flow support: @Query returning Flow<List<DreamEntity>>
        return flow {
            emit(dreamDao.getAllDreams().map { it.toDomain() })
        }
    }
}

// ========== Mapping Extensions ==========

/**
 * Convert Dream domain model to DreamEntity.
 */
private fun Dream.toEntity() = DreamEntity(
    dreamId = id,
    title = title,
    description = description,
    iconName = iconName,
    color = color,
    totalXp = totalXp,
    currentLevel = currentLevel,
    createdAt = createdAt,
    isArchived = isArchived
)

/**
 * Convert DreamEntity to Dream domain model.
 */
private fun DreamEntity.toDomain() = Dream(
    id = dreamId,
    title = title,
    description = description,
    iconName = iconName,
    color = color,
    totalXp = totalXp,
    currentLevel = currentLevel,
    createdAt = createdAt,
    isArchived = isArchived
)
