package com.secretary.helloworld.features.statistics.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for completions table.
 * Phase 4.5.3 Wave 5: Converted to Kotlin
 *
 * Provides type-safe database queries for completion history and analytics.
 */
@Dao
interface CompletionDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new completion record
     * @return The ID of the inserted completion
     */
    @Insert
    fun insertCompletion(completion: CompletionEntity): Long

    /**
     * Get all completion records for a specific task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC")
    fun getCompletionsForTask(taskId: Long): List<CompletionEntity>

    /**
     * Get completion history for all tasks
     */
    @Query("SELECT * FROM completions ORDER BY completed_at DESC")
    fun getAllCompletions(): List<CompletionEntity>

    /**
     * Get completions within a date range
     */
    @Query("SELECT * FROM completions WHERE completed_at BETWEEN :startTime AND :endTime ORDER BY completed_at DESC")
    fun getCompletionsInRange(startTime: Long, endTime: Long): List<CompletionEntity>

    // ========== Statistics Queries ==========

    /**
     * Get average completion time for a specific task
     */
    @Query("SELECT AVG(time_spent_minutes) FROM completions WHERE task_id = :taskId AND time_spent_minutes > 0")
    fun getAverageCompletionTime(taskId: Long): Int

    /**
     * Get count of completions for a task
     */
    @Query("SELECT COUNT(*) FROM completions WHERE task_id = :taskId")
    fun getCompletionCount(taskId: Long): Int

    /**
     * Get total completions today
     */
    @Query("SELECT COUNT(*) FROM completions WHERE DATE(completed_at/1000, 'unixepoch') = DATE('now')")
    fun getCompletionsToday(): Int

    /**
     * Get total completions in last 7 days
     */
    @Query("SELECT COUNT(*) FROM completions WHERE completed_at >= :sevenDaysAgo")
    fun getCompletionsLast7Days(sevenDaysAgo: Long): Int

    /**
     * Get average difficulty for a task
     */
    @Query("SELECT AVG(difficulty) FROM completions WHERE task_id = :taskId")
    fun getAverageDifficulty(taskId: Long): Double

    /**
     * Get most recent completion for a task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC LIMIT 1")
    fun getLatestCompletionForTask(taskId: Long): CompletionEntity?

    // ========== Advanced Analytics ==========

    /**
     * Get daily completion counts for the last N days
     */
    @Query("""
        SELECT DATE(completed_at/1000, 'unixepoch') as date, COUNT(*) as count
        FROM completions
        WHERE completed_at >= :startTime
        GROUP BY DATE(completed_at/1000, 'unixepoch')
        ORDER BY date DESC
    """)
    fun getDailyCompletionCounts(startTime: Long): List<DailyCompletionCount>

    /**
     * Get total time spent on a task
     */
    @Query("SELECT SUM(time_spent_minutes) FROM completions WHERE task_id = :taskId")
    fun getTotalTimeSpentOnTask(taskId: Long): Int

    /**
     * Delete all completions for a task (when task is deleted)
     */
    @Query("DELETE FROM completions WHERE task_id = :taskId")
    fun deleteCompletionsForTask(taskId: Long)

    /**
     * POJO for daily completion count query result
     */
    data class DailyCompletionCount(
        val date: String,
        val count: Int
    )
}
