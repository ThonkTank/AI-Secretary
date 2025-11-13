package com.secretary.helloworld.features.tasks.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Room DAO for tasks table.
 * Phase 4.5.3: Data Layer - Room Migration
 *
 * Provides type-safe database queries for task operations.
 * Replaces manual SQL queries from TaskDatabaseHelper.
 */
@Dao
public interface TaskDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new task
     * @return The ID of the inserted task
     */
    @Insert
    long insertTask(TaskEntity task);

    /**
     * Update an existing task
     */
    @Update
    void updateTask(TaskEntity task);

    /**
     * Delete a task
     */
    @Delete
    void deleteTask(TaskEntity task);

    /**
     * Get all tasks, ordered by completion status, priority, and creation date
     */
    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, priority DESC, created_at DESC")
    List<TaskEntity> getAllTasks();

    /**
     * Get active (not completed) tasks
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY priority DESC, created_at DESC")
    List<TaskEntity> getActiveTasks();

    /**
     * Get completed tasks
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY created_at DESC")
    List<TaskEntity> getCompletedTasks();

    /**
     * Get a single task by ID
     */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    TaskEntity getTaskById(long taskId);

    // ========== Statistics Queries ==========

    /**
     * Get total count of all tasks
     */
    @Query("SELECT COUNT(*) FROM tasks")
    int getTaskCount();

    /**
     * Get count of tasks completed today
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND DATE(last_completed_date/1000, 'unixepoch') = DATE('now')")
    int getTasksCompletedToday();

    /**
     * Get count of tasks completed in the last 7 days
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND last_completed_date >= :sevenDaysAgo")
    int getTasksCompletedLast7Days(long sevenDaysAgo);

    /**
     * Get count of overdue tasks
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date > 0 AND due_date < :currentTime")
    int getOverdueTasksCount(long currentTime);

    // ========== Category Queries ==========

    /**
     * Get all unique categories
     */
    @Query("SELECT DISTINCT category FROM tasks WHERE category IS NOT NULL ORDER BY category ASC")
    List<String> getAllCategories();

    /**
     * Get tasks by category
     */
    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY is_completed ASC, priority DESC")
    List<TaskEntity> getTasksByCategory(String category);

    // ========== Recurrence Queries ==========

    /**
     * Get all recurring tasks (INTERVAL and FREQUENCY types)
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type > 0")
    List<TaskEntity> getRecurringTasks();

    /**
     * Get INTERVAL recurring tasks that are due (completed and past their interval)
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type = 1 AND is_completed = 1 AND due_date <= :currentTime")
    List<TaskEntity> getDueIntervalTasks(long currentTime);

    /**
     * Get FREQUENCY recurring tasks
     */
    @Query("SELECT * FROM tasks WHERE recurrence_type = 2")
    List<TaskEntity> getFrequencyTasks();

    // ========== Streak Queries ==========

    /**
     * Get tasks with active streaks
     */
    @Query("SELECT * FROM tasks WHERE current_streak > 0 ORDER BY current_streak DESC")
    List<TaskEntity> getTasksWithStreaks();

    /**
     * Get task with longest streak
     */
    @Query("SELECT * FROM tasks ORDER BY longest_streak DESC LIMIT 1")
    TaskEntity getTaskWithLongestStreak();

    // ========== Priority Queries ==========

    /**
     * Get tasks by priority level
     */
    @Query("SELECT * FROM tasks WHERE priority = :priority AND is_completed = 0 ORDER BY created_at DESC")
    List<TaskEntity> getTasksByPriority(int priority);

    /**
     * Get high priority active tasks (priority >= 2)
     */
    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND priority >= 2 ORDER BY priority DESC, due_date ASC")
    List<TaskEntity> getHighPriorityTasks();
}
