package com.secretary.features.tasks.domain.usecase

import com.secretary.features.tasks.domain.repository.TaskRepository

/**
 * Use Case: Delete a task
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Delete task with validation
 * Max 40 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 */
class DeleteTaskUseCase(
    private val taskRepository: TaskRepository
) {
    /**
     * Execute task deletion with validation
     *
     * @param taskId ID of task to delete
     * @return Result indicating success or error
     */
    suspend operator fun invoke(taskId: Long): Result<Unit> {
        return try {
            // Validation
            if (taskId <= 0) {
                throw ValidationException("Invalid task ID")
            }

            // Delete task
            taskRepository.deleteTask(taskId)
            Result.success(Unit)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete task: ${e.message}", e))
        }
    }
}
