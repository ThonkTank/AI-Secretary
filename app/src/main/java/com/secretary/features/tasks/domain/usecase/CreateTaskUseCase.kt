package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository

/**
 * Use Case: Create a new task with validation
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Validate and create new tasks
 * Max 50 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 */
class CreateTaskUseCase(
    private val taskRepository: TaskRepository
) {
    /**
     * Execute task creation with validation
     *
     * @param task Task to create
     * @return Result with task ID or error
     */
    suspend operator fun invoke(task: Task): Result<Long> {
        return try {
            // Validation
            validateTask(task)

            // Create task
            val taskId = taskRepository.insertTask(task)
            Result.success(taskId)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to create task: ${e.message}", e))
        }
    }

    private fun validateTask(task: Task) {
        if (task.title.isBlank()) {
            throw ValidationException("Task title cannot be empty")
        }

        if (task.title.length > 200) {
            throw ValidationException("Task title too long (max 200 characters)")
        }
    }
}

/**
 * Validation exception for business rule violations
 */
class ValidationException(message: String) : Exception(message)
