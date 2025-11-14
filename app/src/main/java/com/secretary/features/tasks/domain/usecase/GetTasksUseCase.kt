package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use Case: Retrieve tasks with various filters
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Provide task retrieval with filtering options
 * Max 60 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 */
class GetTasksUseCase(
    private val taskRepository: TaskRepository
) {
    /**
     * Get all tasks
     *
     * @return Result with list of all tasks
     */
    suspend operator fun invoke(): Result<List<Task>> {
        return try {
            val tasks = taskRepository.getAllTasks()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get tasks: ${e.message}", e))
        }
    }

    /**
     * Get only active (uncompleted) tasks
     *
     * @return Result with list of active tasks
     */
    suspend fun getActiveTasks(): Result<List<Task>> {
        return try {
            val tasks = taskRepository.getActiveTasks()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get active tasks: ${e.message}", e))
        }
    }

    /**
     * Get task by ID
     *
     * @param taskId ID of task to retrieve
     * @return Result with task or error if not found
     */
    suspend fun getTaskById(taskId: Long): Result<Task> {
        return try {
            val task = taskRepository.getTaskById(taskId)
                ?: throw ValidationException("Task not found: $taskId")
            Result.success(task)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get task: ${e.message}", e))
        }
    }
}
