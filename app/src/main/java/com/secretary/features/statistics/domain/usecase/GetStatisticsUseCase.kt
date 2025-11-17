package com.secretary.features.statistics.domain.usecase

import com.secretary.Task
import com.secretary.features.statistics.domain.model.TaskStatistics
import com.secretary.features.statistics.domain.repository.CompletionRepository
import com.secretary.features.tasks.domain.repository.TaskRepository

/**
 * Use Case: Get Task Statistics
 * Phase 4: Motivation & Statistics
 *
 * Single Responsibility: Aggregate statistics from multiple repositories
 * Follows Clean Architecture - domain layer with NO Android dependencies
 *
 * @param completionRepository Repository for completion data
 * @param taskRepository Repository for task data
 */
class GetStatisticsUseCase(
    private val completionRepository: CompletionRepository,
    private val taskRepository: TaskRepository
) {

    /**
     * Retrieve aggregated task statistics.
     *
     * Aggregates:
     * - Completions today (from CompletionRepository)
     * - Completions this week (from CompletionRepository)
     * - Active tasks count (from TaskRepository)
     * - Total tasks count (from TaskRepository)
     *
     * @return Result containing TaskStatistics or error
     */
    suspend operator fun invoke(): Result<TaskStatistics> {
        return try {
            // Get completion counts
            val completedToday = completionRepository.getCompletionCountToday()
            val completedThisWeek = completionRepository.getCompletionCountLast7Days()

            // Get task counts
            val allTasks = taskRepository.getAllTasks()
            val totalTasks = allTasks.size
            val activeTasks = allTasks.count { !it.isCompleted }

            // Create statistics model
            val statistics = TaskStatistics(
                completedToday = completedToday,
                completedThisWeek = completedThisWeek,
                activeTasks = activeTasks,
                totalTasks = totalTasks
            )

            Result.success(statistics)

        } catch (e: Exception) {
            Result.failure(
                StatisticsException("Failed to retrieve statistics: ${e.message}", e)
            )
        }
    }

    /**
     * Get completion count for today only.
     * Convenience method for quick queries.
     *
     * @return Number of tasks completed today, or 0 on error
     */
    suspend fun getCompletedTodayCount(): Int {
        return try {
            completionRepository.getCompletionCountToday()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get completion count for this week.
     * Convenience method for quick queries.
     *
     * @return Number of tasks completed in last 7 days, or 0 on error
     */
    suspend fun getCompletedThisWeekCount(): Int {
        return try {
            completionRepository.getCompletionCountLast7Days()
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * Exception thrown when statistics retrieval fails.
 */
class StatisticsException(message: String, cause: Throwable? = null) : Exception(message, cause)
