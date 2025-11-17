package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

/**
 * Unit tests for UpdateTaskUseCase
 * Phase 4.5.7: Testing & Documentation
 */
class UpdateTaskUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var updateTaskUseCase: UpdateTaskUseCase

    @Before
    fun setUp() {
        taskRepository = mock(TaskRepository::class.java)
        updateTaskUseCase = UpdateTaskUseCase(taskRepository)
    }

    @Test
    fun `invoke with valid task succeeds`() = runTest {
        val task = Task(id = 1L, title = "Updated Task")

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isSuccess)
        verify(taskRepository).updateTask(task)
    }

    @Test
    fun `invoke with invalid ID fails`() = runTest {
        val task = Task(id = 0L, title = "Test")

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Invalid task ID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with negative ID fails`() = runTest {
        val task = Task(id = -1L, title = "Test")

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
    }

    @Test
    fun `invoke with blank title fails`() = runTest {
        val task = Task(id = 1L, title = "")

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertEquals("Task title cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with title too long fails`() = runTest {
        val task = Task(id = 1L, title = "a".repeat(201))

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertEquals("Task title too long (max 200 characters)", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        val task = Task(id = 1L, title = "Test")
        `when`(taskRepository.updateTask(any())).thenThrow(RuntimeException("Error"))

        val result = updateTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to update task") == true)
    }
}
