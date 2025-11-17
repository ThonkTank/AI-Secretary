package com.secretary.features.tasks.domain.usecase

import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.verify

/**
 * Unit tests for DeleteTaskUseCase
 * Phase 4.5.7: Testing & Documentation
 */
class DeleteTaskUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase

    @Before
    fun setUp() {
        taskRepository = mock(TaskRepository::class.java)
        deleteTaskUseCase = DeleteTaskUseCase(taskRepository)
    }

    @Test
    fun `invoke with valid ID succeeds`() = runTest {
        val result = deleteTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isSuccess)
        verify(taskRepository).deleteTask(1L)
    }

    @Test
    fun `invoke with invalid ID fails`() = runTest {
        val result = deleteTaskUseCase.invoke(taskId = 0L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Invalid task ID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with negative ID fails`() = runTest {
        val result = deleteTaskUseCase.invoke(taskId = -5L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        `when`(taskRepository.deleteTask(1L)).thenThrow(RuntimeException("Error"))

        val result = deleteTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to delete task") == true)
    }

    @Test
    fun `invoke successfully deletes multiple tasks`() = runTest {
        val result1 = deleteTaskUseCase.invoke(taskId = 1L)
        val result2 = deleteTaskUseCase.invoke(taskId = 2L)
        val result3 = deleteTaskUseCase.invoke(taskId = 3L)

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertTrue(result3.isSuccess)
        verify(taskRepository).deleteTask(1L)
        verify(taskRepository).deleteTask(2L)
        verify(taskRepository).deleteTask(3L)
    }
}
