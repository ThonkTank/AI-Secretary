package com.secretary.features.tasks.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretary.Task
import com.secretary.features.tasks.domain.usecase.CompleteTaskUseCase
import com.secretary.features.tasks.domain.usecase.DeleteTaskUseCase
import com.secretary.features.tasks.domain.usecase.GetTasksUseCase
import kotlinx.coroutines.launch

/**
 * ViewModel: Task List Management
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Manage task list UI state
 * Max 150 lines (Architecture Standard)
 *
 * @param getTasksUseCase Use case for retrieving tasks
 * @param deleteTaskUseCase Use case for deleting tasks
 * @param completeTaskUseCase Use case for completing tasks
 */
class TaskListViewModel(
    private val getTasksUseCase: GetTasksUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase
) : ViewModel() {

    // UI State
    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _operationSuccess = MutableLiveData<String?>()
    val operationSuccess: LiveData<String?> = _operationSuccess

    /**
     * Load all tasks from repository
     */
    fun loadTasks() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            getTasksUseCase().fold(
                onSuccess = { taskList ->
                    _tasks.value = taskList
                    _loading.value = false
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to load tasks"
                    _loading.value = false
                }
            )
        }
    }

    /**
     * Load only active (uncompleted) tasks
     */
    fun loadActiveTasks() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            getTasksUseCase.getActiveTasks().fold(
                onSuccess = { taskList ->
                    _tasks.value = taskList
                    _loading.value = false
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to load active tasks"
                    _loading.value = false
                }
            )
        }
    }

    /**
     * Delete a task by ID
     *
     * @param taskId ID of task to delete
     */
    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            _error.value = null

            deleteTaskUseCase(taskId).fold(
                onSuccess = {
                    _operationSuccess.value = "Task deleted"
                    loadTasks() // Refresh list
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to delete task"
                }
            )
        }
    }

    /**
     * Mark task as complete (with streak and recurrence logic)
     *
     * @param taskId ID of task to complete
     */
    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            _error.value = null

            completeTaskUseCase(taskId).fold(
                onSuccess = {
                    _operationSuccess.value = "Task completed"
                    loadTasks() // Refresh list
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to complete task"
                }
            )
        }
    }

    /**
     * Refresh task list (convenience method)
     */
    fun refresh() {
        loadTasks()
    }

    /**
     * Clear error message (e.g., after user dismisses error dialog)
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clear operation success message
     */
    fun clearOperationSuccess() {
        _operationSuccess.value = null
    }
}
