package com.secretary.features.statistics.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for completions table.
 * Phase 4.5.3 Wave 10 Step 5: Completion Repository Pattern
 *
 * Provides type-safe database queries for completion history and analytics.
 * All methods are suspend functions for Kotlin coroutine support.
 */
@Dao
interface CompletionDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new completion record
     * @return The ID of the inserted completion
     */
    @Insert
    suspend fun insertCompletion(completion: CompletionEntity): Long

    /**
     * Get all completion records for a specific task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC")
    suspend fun getCompletionsForTask(taskId: Long): List<CompletionEntity>

    /**
     * Get completion history for all tasks
     */
    @Query("SELECT * FROM completions ORDER BY completed_at DESC")
    suspend fun getAllCompletions(): List<CompletionEntity>

    /**
     * Get completions after a specific timestamp
     */
    @Query("SELECT * FROM completions WHERE completed_at >= :timestamp ORDER BY completed_at DESC")
    suspend fun getCompletionsAfter(timestamp: Long): List<CompletionEntity>

    // ========== Statistics Queries ==========

    /**
     * Get average completion time for a specific task
     */
    @Query("SELECT AVG(time_spent_minutes) FROM completions WHERE task_id = :taskId AND time_spent_minutes > 0")
    suspend fun getAverageCompletionTime(taskId: Long): Int?

    /**
     * Get count of completions for a task
     */
    @Query("SELECT COUNT(*) FROM completions WHERE task_id = :taskId")
    suspend fun getCompletionCount(taskId: Long): Int

    /**
     * Get count of completions after a specific timestamp
     */
    @Query("SELECT COUNT(*) FROM completions WHERE completed_at >= :timestamp")
    suspend fun getCompletionCountAfter(timestamp: Long): Int

    /**
     * Get average difficulty for a task
     */
    @Query("SELECT AVG(difficulty) FROM completions WHERE task_id = :taskId")
    suspend fun getAverageDifficulty(taskId: Long): Double?

    /**
     * Get most recent completion for a task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC LIMIT 1")
    suspend fun getLatestCompletionForTask(taskId: Long): CompletionEntity?

    // ========== Advanced Analytics ==========

    /**
     * Get total time spent on a task
     */
    @Query("SELECT SUM(time_spent_minutes) FROM completions WHERE task_id = :taskId")
    suspend fun getTotalTimeSpentOnTask(taskId: Long): Int?

    /**
     * Delete all completions for a task (when task is deleted)
     */
    @Query("DELETE FROM completions WHERE task_id = :taskId")
    suspend fun deleteCompletionsForTask(taskId: Long)
}
