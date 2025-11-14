package com.secretary.features.tasks.domain.repository

import com.secretary.Task

/**
 * Repository interface for Task CRUD operations.
 * Phase 4.5.3 Wave 10 Step 2: Task Repository Pattern
 *
 * Provides abstraction layer between presentation and data layers.
 * Implementation uses Room DAO for database operations.
 *
 * Clean Architecture: Domain layer defines interface, Data layer implements.
 */
interface TaskRepository {

    // ========== CRUD Operations ==========

    /**
     * Insert a new task
     * @return The ID of the inserted task
     */
    suspend fun insertTask(task: Task): Long

    /**
     * Update an existing task
     */
    suspend fun updateTask(task: Task)

    /**
     * Delete a task by ID
     */
    suspend fun deleteTask(taskId: Long)

    /**
     * Get all tasks
     * @return List of all tasks (active and completed)
     */
    suspend fun getAllTasks(): List<Task>

    /**
     * Get active (not completed) tasks
     * @return List of active tasks only
     */
    suspend fun getActiveTasks(): List<Task>

    /**
     * Get a single task by ID
     * @return Task if found, null otherwise
     */
    suspend fun getTaskById(taskId: Long): Task?

    // ========== Category Operations ==========

    /**
     * Get all unique categories used in tasks
     * @return List of category names
     */
    suspend fun getAllCategories(): List<String>

    // ========== Statistics Operations ==========

    /**
     * Get total count of all tasks
     */
    suspend fun getTaskCount(): Int

    /**
     * Get count of tasks completed today
     */
    suspend fun getTasksCompletedToday(): Int

    /**
     * Get count of tasks completed in the last 7 days
     */
    suspend fun getTasksCompletedLast7Days(): Int

    /**
     * Get count of overdue tasks (not completed and past due date)
     */
    suspend fun getOverdueTasksCount(): Int
}
