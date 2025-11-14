package com.secretary.features.tasks.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.domain.service.RecurrenceService
import com.secretary.features.tasks.domain.service.StreakService
import com.secretary.features.tasks.domain.usecase.CompleteTaskUseCase
import com.secretary.features.tasks.domain.usecase.CreateTaskUseCase
import com.secretary.features.tasks.domain.usecase.DeleteTaskUseCase
import com.secretary.features.tasks.domain.usecase.GetTasksUseCase
import com.secretary.features.tasks.domain.usecase.UpdateTaskUseCase

/**
 * ViewModelFactory: Dependency Injection for ViewModels
 * Phase 4.5.5 Wave 12 Phase 3: Integration & Migration
 *
 * Single Responsibility: Create ViewModels with proper dependencies
 * Max 70 lines (Architecture Standard)
 *
 * @param taskRepository Repository for task data access
 * @param streakService Service for streak calculation
 * @param recurrenceService Service for recurrence logic
 */
class TaskViewModelFactory(
    private val taskRepository: TaskRepository,
    private val streakService: StreakService,
    private val recurrenceService: RecurrenceService
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TaskListViewModel::class.java) -> {
                // Create Use Cases for TaskListViewModel
                val getTasksUseCase = GetTasksUseCase(taskRepository)
                val deleteTaskUseCase = DeleteTaskUseCase(taskRepository)
                val completeTaskUseCase = CompleteTaskUseCase(
                    taskRepository,
                    streakService,
                    recurrenceService
                )

                TaskListViewModel(
                    getTasksUseCase,
                    deleteTaskUseCase,
                    completeTaskUseCase
                ) as T
            }

            modelClass.isAssignableFrom(TaskDetailViewModel::class.java) -> {
                // Create Use Cases for TaskDetailViewModel
                val createTaskUseCase = CreateTaskUseCase(taskRepository)
                val updateTaskUseCase = UpdateTaskUseCase(taskRepository)
                val getTasksUseCase = GetTasksUseCase(taskRepository)

                TaskDetailViewModel(
                    createTaskUseCase,
                    updateTaskUseCase,
                    getTasksUseCase
                ) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
