package com.secretary.features.tasks.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretary.Task
import com.secretary.features.tasks.domain.usecase.CreateTaskUseCase
import com.secretary.features.tasks.domain.usecase.GetTasksUseCase
import com.secretary.features.tasks.domain.usecase.UpdateTaskUseCase
import kotlinx.coroutines.launch

/**
 * ViewModel: Task Detail (Create/Edit)
 * Phase 4.5.5 Wave 12: Domain Layer Integration
 *
 * Single Responsibility: Manage single task create/edit UI state
 * Max 120 lines (Architecture Standard)
 *
 * @param createTaskUseCase Use case for creating tasks
 * @param updateTaskUseCase Use case for updating tasks
 * @param getTasksUseCase Use case for retrieving tasks
 */
class TaskDetailViewModel(
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val getTasksUseCase: GetTasksUseCase
) : ViewModel() {

    // UI State
    private val _task = MutableLiveData<Task?>()
    val task: LiveData<Task?> = _task

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _saveResult = MutableLiveData<Result<Unit>?>()
    val saveResult: LiveData<Result<Unit>?> = _saveResult

    private val _validationError = MutableLiveData<String?>()
    val validationError: LiveData<String?> = _validationError

    /**
     * Load task by ID for editing
     *
     * @param taskId ID of task to load (0 or negative for new task)
     */
    fun loadTask(taskId: Long) {
        if (taskId <= 0) {
            // New task - initialize empty
            _task.value = null
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _validationError.value = null

            getTasksUseCase.getTaskById(taskId).fold(
                onSuccess = { taskData ->
                    _task.value = taskData
                    _loading.value = false
                },
                onFailure = { exception ->
                    _validationError.value = exception.message ?: "Failed to load task"
                    _loading.value = false
                }
            )
        }
    }

    /**
     * Save task (create or update based on ID)
     *
     * @param task Task to save
     */
    fun saveTask(task: Task) {
        viewModelScope.launch {
            _loading.value = true
            _validationError.value = null
            _saveResult.value = null

            val result = if (task.id <= 0) {
                // Create new task
                createTaskUseCase(task).map { Unit }
            } else {
                // Update existing task
                updateTaskUseCase(task)
            }

            _saveResult.value = result
            _loading.value = false

            result.fold(
                onSuccess = { /* Success handled by observer */ },
                onFailure = { exception ->
                    _validationError.value = exception.message ?: "Failed to save task"
                }
            )
        }
    }

    /**
     * Update current task in state (for form binding)
     *
     * @param task Updated task data
     */
    fun updateTaskData(task: Task) {
        _task.value = task
    }

    /**
     * Clear validation error (e.g., after user dismisses error dialog)
     */
    fun clearValidationError() {
        _validationError.value = null
    }

    /**
     * Clear save result (e.g., after handling success)
     */
    fun clearSaveResult() {
        _saveResult.value = null
    }
}
