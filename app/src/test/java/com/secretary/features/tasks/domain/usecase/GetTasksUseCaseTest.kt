package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

/**
 * Unit tests for GetTasksUseCase
 * Phase 4.5.7: Testing & Documentation
 */
class GetTasksUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var getTasksUseCase: GetTasksUseCase

    @Before
    fun setUp() {
        taskRepository = mock(TaskRepository::class.java)
        getTasksUseCase = GetTasksUseCase(taskRepository)
    }

    @Test
    fun `invoke returns all tasks successfully`() = runTest {
        val tasks = listOf(
            Task(id = 1L, title = "Task 1"),
            Task(id = 2L, title = "Task 2"),
            Task(id = 3L, title = "Task 3")
        )
        whenever(taskRepository.getAllTasks()).thenReturn(tasks)

        val result = getTasksUseCase.invoke()

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        assertEquals(tasks, result.getOrNull())
    }

    @Test
    fun `invoke returns empty list when no tasks`() = runTest {
        whenever(taskRepository.getAllTasks()).thenReturn(emptyList())

        val result = getTasksUseCase.invoke()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        whenever(taskRepository.getAllTasks()).thenThrow(RuntimeException("DB error"))

        val result = getTasksUseCase.invoke()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to get tasks") == true)
    }

    @Test
    fun `getActiveTasks returns only active tasks`() = runTest {
        val activeTasks = listOf(
            Task(id = 1L, title = "Active 1", isCompleted = false),
            Task(id = 2L, title = "Active 2", isCompleted = false)
        )
        whenever(taskRepository.getActiveTasks()).thenReturn(activeTasks)

        val result = getTasksUseCase.getActiveTasks()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertTrue(result.getOrNull()!!.all { !it.isCompleted })
    }

    @Test
    fun `getActiveTasks handles repository exception`() = runTest {
        whenever(taskRepository.getActiveTasks()).thenThrow(RuntimeException("Error"))

        val result = getTasksUseCase.getActiveTasks()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to get active tasks") == true)
    }

    @Test
    fun `getTaskById returns task when found`() = runTest {
        val task = Task(id = 1L, title = "Test Task")
        whenever(taskRepository.getTaskById(1L)).thenReturn(task)

        val result = getTasksUseCase.getTaskById(1L)

        assertTrue(result.isSuccess)
        assertEquals(task, result.getOrNull())
    }

    @Test
    fun `getTaskById fails when task not found`() = runTest {
        whenever(taskRepository.getTaskById(99L)).thenReturn(null)

        val result = getTasksUseCase.getTaskById(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Task not found: 99", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getTaskById handles repository exception`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenThrow(RuntimeException("Error"))

        val result = getTasksUseCase.getTaskById(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to get task") == true)
    }
}
