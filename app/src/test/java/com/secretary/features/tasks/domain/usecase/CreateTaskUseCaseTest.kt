package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for CreateTaskUseCase
 * Phase 4.5.7: Testing & Documentation
 */
class CreateTaskUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var createTaskUseCase: CreateTaskUseCase

    @Before
    fun setUp() {
        taskRepository = mock(TaskRepository::class.java)
        createTaskUseCase = CreateTaskUseCase(taskRepository)
    }

    @Test
    fun `invoke with valid task succeeds`() = runTest {
        val task = Task(title = "Test Task")
        whenever(taskRepository.insertTask(any())).thenReturn(1L)

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        verify(taskRepository).insertTask(task)
    }

    @Test
    fun `invoke with blank title fails`() = runTest {
        val task = Task(title = "")

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Task title cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with whitespace-only title fails`() = runTest {
        val task = Task(title = "   ")

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
    }

    @Test
    fun `invoke with title too long fails`() = runTest {
        val longTitle = "a".repeat(201)
        val task = Task(title = longTitle)

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertEquals("Task title too long (max 200 characters)", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with title exactly 200 chars succeeds`() = runTest {
        val maxTitle = "a".repeat(200)
        val task = Task(title = maxTitle)
        whenever(taskRepository.insertTask(any())).thenReturn(1L)

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        val task = Task(title = "Test")
        whenever(taskRepository.insertTask(any())).thenThrow(RuntimeException("DB error"))

        val result = createTaskUseCase.invoke(task)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to create task") == true)
    }
}
