package com.secretary.features.statistics.data

import com.secretary.features.statistics.domain.model.Completion
import com.secretary.features.statistics.domain.repository.CompletionRepository
import java.util.Calendar

/**
 * Implementation of CompletionRepository using Room.
 * Phase 4.5.3 Wave 10 Step 5: Completion Repository Pattern
 *
 * Handles conversion between CompletionEntity (data layer) and Completion (domain layer).
 * Provides completion tracking and statistics operations.
 */
class CompletionRepositoryImpl(
    private val completionDao: CompletionDao
) : CompletionRepository {

    override suspend fun saveCompletion(
        taskId: Long,
        timeSpentMinutes: Int,
        difficulty: Int,
        notes: String?
    ): Long {
        val entity = CompletionEntity(
            taskId = taskId,
            completedAt = System.currentTimeMillis(),
            timeSpentMinutes = timeSpentMinutes,
            difficulty = difficulty,
            notes = notes
        )
        return completionDao.insertCompletion(entity)
    }

    override suspend fun getCompletionHistory(taskId: Long): List<Completion> {
        val entities = completionDao.getCompletionsForTask(taskId)
        return entities.map { it.toDomainModel() }
    }

    override suspend fun getCompletionsToday(): List<Completion> {
        val todayStart = getTodayStart()
        val entities = completionDao.getCompletionsAfter(todayStart)
        return entities.map { it.toDomainModel() }
    }

    override suspend fun getCompletionsLast7Days(): List<Completion> {
        val weekAgo = getWeekAgoStart()
        val entities = completionDao.getCompletionsAfter(weekAgo)
        return entities.map { it.toDomainModel() }
    }

    override suspend fun getAverageCompletionTime(taskId: Long): Int {
        return completionDao.getAverageCompletionTime(taskId) ?: 0
    }

    override suspend fun getCompletionCountToday(): Int {
        val todayStart = getTodayStart()
        return completionDao.getCompletionCountAfter(todayStart)
    }

    override suspend fun getCompletionCountLast7Days(): Int {
        val weekAgo = getWeekAgoStart()
        return completionDao.getCompletionCountAfter(weekAgo)
    }

    /**
     * Get timestamp for start of today (00:00:00.000).
     */
    private fun getTodayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Get timestamp for start of 7 days ago (00:00:00.000).
     */
    private fun getWeekAgoStart(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Convert CompletionEntity to domain Completion model.
     */
    private fun CompletionEntity.toDomainModel(): Completion {
        return Completion(
            completionId = completionId,
            taskId = taskId,
            completedAt = completedAt,
            timeSpentMinutes = timeSpentMinutes,
            difficulty = difficulty,
            notes = notes
        )
    }
}
