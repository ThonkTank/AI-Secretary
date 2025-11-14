package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository

/**
 * Use Case: Update an existing task with validation
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Validate and update existing tasks
 * Max 50 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 */
class UpdateTaskUseCase(
    private val taskRepository: TaskRepository
) {
    /**
     * Execute task update with validation
     *
     * @param task Task to update
     * @return Result indicating success or error
     */
    suspend operator fun invoke(task: Task): Result<Unit> {
        return try {
            // Validation
            validateTask(task)

            // Verify task exists
            if (task.id <= 0) {
                throw ValidationException("Invalid task ID")
            }

            // Update task
            taskRepository.updateTask(task)
            Result.success(Unit)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update task: ${e.message}", e))
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
