package com.secretary.features.tasks.domain.usecase

import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.domain.service.RecurrenceService
import com.secretary.features.tasks.domain.service.StreakService

/**
 * Use Case: Complete a task with streak and recurrence logic
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Orchestrate task completion with all business logic
 * Max 80 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 * @param streakService Service for streak calculation
 * @param recurrenceService Service for recurrence logic
 */
class CompleteTaskUseCase(
    private val taskRepository: TaskRepository,
    private val streakService: StreakService,
    private val recurrenceService: RecurrenceService
) {
    /**
     * Execute task completion with full business logic
     *
     * @param taskId ID of task to complete
     * @param completionTime Timestamp of completion (default: now)
     * @return Result indicating success or error
     */
    suspend operator fun invoke(
        taskId: Long,
        completionTime: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return try {
            // Validation
            if (taskId <= 0) {
                throw ValidationException("Invalid task ID")
            }

            // Get task
            val task = taskRepository.getTaskById(taskId)
                ?: throw ValidationException("Task not found: $taskId")

            // Already completed check
            if (task.isCompleted) {
                throw ValidationException("Task is already completed")
            }

            // Apply business logic in correct order:
            // 1. Update streaks first (based on current task state)
            var updatedTask = streakService.updateStreak(task, completionTime)

            // 2. Apply recurrence logic (which may set completed flag and update due dates)
            updatedTask = recurrenceService.handleRecurringCompletion(updatedTask, completionTime)

            // 3. Save updated task
            taskRepository.updateTask(updatedTask)

            Result.success(Unit)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to complete task: ${e.message}", e))
        }
    }

    /**
     * Complete task with metadata (for completion history tracking)
     *
     * @param taskId ID of task to complete
     * @param timeSpent Minutes spent on task (optional)
     * @param difficulty Difficulty rating 1-5 (optional)
     * @param notes Completion notes (optional)
     * @param completionTime Timestamp of completion (default: now)
     * @return Result indicating success or error
     */
    suspend fun completeWithMetadata(
        taskId: Long,
        timeSpent: Int? = null,
        difficulty: Int? = null,
        notes: String? = null,
        completionTime: Long = System.currentTimeMillis()
    ): Result<Unit> {
        // TODO: Create Completion entity and save to CompletionRepository
        // For now, just complete the task
        return invoke(taskId, completionTime)
    }
}
