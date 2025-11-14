package com.secretary.features.tasks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for tasks table.
 * Phase 4.5.3 Wave 5: Converted to Kotlin
 *
 * Provides type-safe database queries for task operations.
 * Replaces manual SQL queries from TaskDatabaseHelper.
 */
@Dao
interface TaskDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new task
     * @return The ID of the inserted task
     */
    @Insert
    fun insertTask(task: TaskEntity): Long

    /**
     * Update an existing task
     */
    @Update
    fun updateTask(task: TaskEntity)

    /**
     * Delete a task
     */
    @Delete
    fun deleteTask(task: TaskEntity)

    /**
     * Get all tasks, ordered by completion status, priority, and creation date
     */
    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, priority DESC, created_at DESC")
    fun getAllTasks(): List<TaskEntity>

    /**
     * Get active (not completed) tasks
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY priority DESC, created_at DESC")
    fun getActiveTasks(): List<TaskEntity>

    /**
     * Get completed tasks
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY created_at DESC")
    fun getCompletedTasks(): List<TaskEntity>

    /**
     * Get a single task by ID
     */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: Long): TaskEntity?

    // ========== Statistics Queries ==========

    /**
     * Get total count of all tasks
     */
    @Query("SELECT COUNT(*) FROM tasks")
    fun getTaskCount(): Int

    /**
     * Get count of tasks completed today
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND DATE(last_completed_date/1000, 'unixepoch') = DATE('now')")
    fun getTasksCompletedToday(): Int

    /**
     * Get count of tasks completed in the last 7 days
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND last_completed_date >= :sevenDaysAgo")
    fun getTasksCompletedLast7Days(sevenDaysAgo: Long): Int

    /**
     * Get count of overdue tasks
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date > 0 AND due_date < :currentTime")
    fun getOverdueTasksCount(currentTime: Long): Int

    // ========== Category Queries ==========

    /**
     * Get all unique categories
     */
    @Query("SELECT DISTINCT category FROM tasks WHERE category IS NOT NULL ORDER BY category ASC")
    fun getAllCategories(): List<String>

    /**
     * Get tasks by category
     */
    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY is_completed ASC, priority DESC")
    fun getTasksByCategory(category: String): List<TaskEntity>

    // ========== Recurrence Queries ==========

    /**
     * Get all recurring tasks (INTERVAL and FREQUENCY types)
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type > 0")
    fun getRecurringTasks(): List<TaskEntity>

    /**
     * Get INTERVAL recurring tasks that are due (completed and past their interval)
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type = 1 AND is_completed = 1 AND due_date <= :currentTime")
    fun getDueIntervalTasks(currentTime: Long): List<TaskEntity>

    /**
     * Get FREQUENCY recurring tasks
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type = 2")
    fun getFrequencyTasks(): List<TaskEntity>

    // ========== Streak Queries ==========

    /**
     * Get tasks with active streaks
     */
    @Query("SELECT * FROM tasks WHERE current_streak > 0 ORDER BY current_streak DESC")
    fun getTasksWithStreaks(): List<TaskEntity>

    /**
     * Get task with longest streak
     */
    @Query("SELECT * FROM tasks ORDER BY longest_streak DESC LIMIT 1")
    fun getTaskWithLongestStreak(): TaskEntity?

    // ========== Priority Queries ==========

    /**
     * Get tasks by priority level
     */
    @Query("SELECT * FROM tasks WHERE priority = :priority AND is_completed = 0 ORDER BY created_at DESC")
    fun getTasksByPriority(priority: Int): List<TaskEntity>

    /**
     * Get high priority active tasks (priority >= 2)
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND priority >= 2 ORDER BY priority DESC, due_date ASC")
    fun getHighPriorityTasks(): List<TaskEntity>
}
