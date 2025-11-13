package com.secretary.helloworld.features.statistics.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Room DAO for completions table.
 * Phase 4.5.3: Data Layer - Room Migration
 *
 * Provides type-safe database queries for completion history and analytics.
 */
@Dao
public interface CompletionDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new completion record
     * @return The ID of the inserted completion
     */
    @Insert
    long insertCompletion(CompletionEntity completion);

    /**
     * Get all completion records for a specific task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC")
    List<CompletionEntity> getCompletionsForTask(long taskId);

    /**
     * Get completion history for all tasks
     */
    @Query("SELECT * FROM completions ORDER BY completed_at DESC")
    List<CompletionEntity> getAllCompletions();

    /**
     * Get completions within a date range
     */
    @Query("SELECT * FROM completions WHERE completed_at BETWEEN :startTime AND :endTime ORDER BY completed_at DESC")
    List<CompletionEntity> getCompletionsInRange(long startTime, long endTime);

    // ========== Statistics Queries ==========

    /**
     * Get average completion time for a specific task
     */
    @Query("SELECT AVG(time_spent_minutes) FROM completions WHERE task_id = :taskId AND time_spent_minutes > 0")
    int getAverageCompletionTime(long taskId);

    /**
     * Get count of completions for a task
     */
    @Query("SELECT COUNT(*) FROM completions WHERE task_id = :taskId")
    int getCompletionCount(long taskId);

    /**
     * Get total completions today
     */
    @Query("SELECT COUNT(*) FROM completions WHERE DATE(completed_at/1000, 'unixepoch') = DATE('now')")
    int getCompletionsToday();

    /**
     * Get total completions in last 7 days
     */
    @Query("SELECT COUNT(*) FROM completions WHERE completed_at >= :sevenDaysAgo")
    int getCompletionsLast7Days(long sevenDaysAgo);

    /**
     * Get average difficulty for a task
     */
    @Query("SELECT AVG(difficulty) FROM completions WHERE task_id = :taskId")
    double getAverageDifficulty(long taskId);

    /**
     * Get most recent completion for a task
     */
    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY completed_at DESC LIMIT 1")
    CompletionEntity getLatestCompletionForTask(long taskId);

    // ========== Advanced Analytics ==========

    /**
     * Get daily completion counts for the last N days
     */
    @Query("SELECT DATE(completed_at/1000, 'unixepoch') as date, COUNT(*) as count " +
           "FROM completions " +
           "WHERE completed_at >= :startTime " +
           "GROUP BY DATE(completed_at/1000, 'unixepoch') " +
           "ORDER BY date DESC")
    List<DailyCompletionCount> getDailyCompletionCounts(long startTime);

    /**
     * Get total time spent on a task
     */
    @Query("SELECT SUM(time_spent_minutes) FROM completions WHERE task_id = :taskId")
    int getTotalTimeSpentOnTask(long taskId);

    /**
     * Delete all completions for a task (when task is deleted)
     */
    @Query("DELETE FROM completions WHERE task_id = :taskId")
    void deleteCompletionsForTask(long taskId);

    /**
     * POJO for daily completion count query result
     */
    class DailyCompletionCount {
        public String date;
        public int count;
    }
}
