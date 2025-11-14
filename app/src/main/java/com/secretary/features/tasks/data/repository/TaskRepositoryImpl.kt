package com.secretary.features.tasks.data.repository

import com.secretary.Task
import com.secretary.features.tasks.data.TaskDao
import com.secretary.features.tasks.data.TaskEntity
import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of TaskRepository using Room DAO.
 * Phase 4.5.3 Wave 10 Step 2: Task Repository Pattern
 *
 * Handles mapping between domain models (Task) and database entities (TaskEntity).
 * All database operations run on IO dispatcher for thread safety.
 */
class TaskRepositoryImpl(private val taskDao: TaskDao) : TaskRepository {

    // ========== CRUD Operations ==========

    override suspend fun insertTask(task: Task): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task.toTaskEntity())
    }

    override suspend fun updateTask(task: Task) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task.toTaskEntity())
    }

    override suspend fun deleteTask(taskId: Long) = withContext(Dispatchers.IO) {
        // Create minimal entity with just ID for deletion
        val entity = TaskEntity(id = taskId)
        taskDao.deleteTask(entity)
    }

    override suspend fun getAllTasks(): List<Task> = withContext(Dispatchers.IO) {
        taskDao.getAllTasks().map { it.toTask() }
    }

    override suspend fun getActiveTasks(): List<Task> = withContext(Dispatchers.IO) {
        taskDao.getActiveTasks().map { it.toTask() }
    }

    override suspend fun getTaskById(taskId: Long): Task? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(taskId)?.toTask()
    }

    // ========== Category Operations ==========

    override suspend fun getAllCategories(): List<String> = withContext(Dispatchers.IO) {
        taskDao.getAllCategories()
    }

    // ========== Statistics Operations ==========

    override suspend fun getTaskCount(): Int = withContext(Dispatchers.IO) {
        taskDao.getTaskCount()
    }

    override suspend fun getTasksCompletedToday(): Int = withContext(Dispatchers.IO) {
        taskDao.getTasksCompletedToday()
    }

    override suspend fun getTasksCompletedLast7Days(): Int = withContext(Dispatchers.IO) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        taskDao.getTasksCompletedLast7Days(sevenDaysAgo)
    }

    override suspend fun getOverdueTasksCount(): Int = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        taskDao.getOverdueTasksCount(currentTime)
    }

    // ========== Mapping Functions ==========

    /**
     * Convert TaskEntity (database) to Task (domain model)
     */
    private fun TaskEntity.toTask() = Task(
        id = id,
        title = title,
        description = description,
        category = category,
        createdAt = createdAt,
        dueDate = dueDate,
        isCompleted = isCompleted == 1, // SQLite boolean as int
        priority = priority,
        recurrenceType = recurrenceType,
        recurrenceAmount = recurrenceAmount,
        recurrenceUnit = recurrenceUnit,
        lastCompletedDate = lastCompletedDate,
        completionsThisPeriod = completionsThisPeriod,
        currentPeriodStart = currentPeriodStart,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        lastStreakDate = lastStreakDate
    )

    /**
     * Convert Task (domain model) to TaskEntity (database)
     */
    private fun Task.toTaskEntity() = TaskEntity(
        id = id,
        title = title,
        description = description,
        category = category,
        createdAt = createdAt,
        dueDate = dueDate,
        isCompleted = if (isCompleted) 1 else 0, // Boolean to SQLite int
        priority = priority,
        recurrenceType = recurrenceType,
        recurrenceAmount = recurrenceAmount,
        recurrenceUnit = recurrenceUnit,
        lastCompletedDate = lastCompletedDate,
        completionsThisPeriod = completionsThisPeriod,
        currentPeriodStart = currentPeriodStart,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        lastStreakDate = lastStreakDate
    )
}
