package com.secretary.features.statistics.domain.repository

import com.secretary.features.statistics.domain.model.Completion

/**
 * Repository interface for Completion operations.
 * Phase 4.5.3 Wave 10 Step 5: Completion Repository Pattern
 *
 * Domain layer interface - implementation in data layer.
 * Follows Repository Pattern for Clean Architecture.
 */
interface CompletionRepository {

    /**
     * Save a new completion record.
     * @param taskId The task being completed
     * @param timeSpentMinutes Time spent on the task (0 if not tracked)
     * @param difficulty Difficulty rating 0-10
     * @param notes Optional notes about the completion
     * @return The ID of the saved completion
     */
    suspend fun saveCompletion(
        taskId: Long,
        timeSpentMinutes: Int,
        difficulty: Int,
        notes: String?
    ): Long

    /**
     * Get completion history for a specific task.
     * @param taskId The task to get history for
     * @return List of completions, ordered by most recent first
     */
    suspend fun getCompletionHistory(taskId: Long): List<Completion>

    /**
     * Get all completions from today (since 00:00:00).
     * @return List of today's completions
     */
    suspend fun getCompletionsToday(): List<Completion>

    /**
     * Get all completions from the last 7 days.
     * @return List of completions from last 7 days
     */
    suspend fun getCompletionsLast7Days(): List<Completion>

    /**
     * Get average completion time for a task.
     * @param taskId The task to calculate average for
     * @return Average time in minutes, or 0 if no data
     */
    suspend fun getAverageCompletionTime(taskId: Long): Int

    /**
     * Get count of completions today.
     * @return Number of completions today
     */
    suspend fun getCompletionCountToday(): Int

    /**
     * Get count of completions in last 7 days.
     * @return Number of completions in last 7 days
     */
    suspend fun getCompletionCountLast7Days(): Int
}
