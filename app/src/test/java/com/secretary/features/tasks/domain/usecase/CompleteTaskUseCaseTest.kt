package com.secretary.features.tasks.domain.usecase

import com.secretary.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.domain.service.RecurrenceService
import com.secretary.features.tasks.domain.service.StreakService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar

/**
 * Unit tests for CompleteTaskUseCase
 * Phase 4.5.7: Testing & Documentation
 *
 * Tests task completion orchestration including:
 * - Validation (invalid ID, task not found, already completed)
 * - Streak updates
 * - Recurrence logic
 * - Repository updates
 *
 * Uses Mockito for mocking dependencies
 * Target: 70%+ code coverage
 */
class CompleteTaskUseCaseTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var streakService: StreakService
    private lateinit var recurrenceService: RecurrenceService
    private lateinit var completeTaskUseCase: CompleteTaskUseCase

    private lateinit var baseTask: Task

    @Before
    fun setUp() {
        // Create mocks
        taskRepository = mock(TaskRepository::class.java)
        streakService = StreakService() // Use real service
        recurrenceService = RecurrenceService() // Use real service

        // Create use case with dependencies
        completeTaskUseCase = CompleteTaskUseCase(
            taskRepository,
            streakService,
            recurrenceService
        )

        // Base task for testing
        baseTask = Task(
            id = 1L,
            title = "Test Task",
            isCompleted = false,
            currentStreak = 0,
            longestStreak = 0,
            lastStreakDate = 0L,
            recurrenceType = Task.RECURRENCE_NONE
        )
    }

    // ========== Validation Tests ==========

    @Test
    fun `invoke with invalid taskId returns failure`() = runTest {
        val result = completeTaskUseCase.invoke(taskId = 0L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Invalid task ID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with negative taskId returns failure`() = runTest {
        val result = completeTaskUseCase.invoke(taskId = -5L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
    }

    @Test
    fun `invoke with non-existent task returns failure`() = runTest {
        whenever(taskRepository.getTaskById(99L)).thenReturn(null)

        val result = completeTaskUseCase.invoke(taskId = 99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Task not found: 99", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with already completed task returns failure`() = runTest {
        val completedTask = baseTask.copy(isCompleted = true)
        whenever(taskRepository.getTaskById(1L)).thenReturn(completedTask)

        val result = completeTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ValidationException)
        assertEquals("Task is already completed", result.exceptionOrNull()?.message)
    }

    // ========== Successful Completion Tests ==========

    @Test
    fun `invoke with valid non-recurring task succeeds`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)

        val result = completeTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isSuccess)
        verify(taskRepository).updateTask(any())
    }

    @Test
    fun `invoke updates streaks correctly`() = runTest {
        val completionTime = createTimestamp(2024, 1, 15)
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = completionTime)

        verify(taskRepository).updateTask(check { task ->
            assertEquals(1, task.currentStreak)
            assertEquals(1, task.longestStreak)
            assertTrue(task.lastStreakDate > 0)
        })
    }

    @Test
    fun `invoke marks non-recurring task as completed`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)

        completeTaskUseCase.invoke(taskId = 1L)

        verify(taskRepository).updateTask(check { task ->
            assertTrue(task.isCompleted)
        })
    }

    @Test
    fun `invoke handles INTERVAL recurrence correctly`() = runTest {
        val intervalTask = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_DAY
        )
        val completionTime = createTimestamp(2024, 1, 15)
        whenever(taskRepository.getTaskById(1L)).thenReturn(intervalTask)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = completionTime)

        verify(taskRepository).updateTask(check { task ->
            assertTrue(task.isCompleted) // Marked completed
            assertTrue(task.dueDate > completionTime) // Next due date set
            assertEquals(1, task.currentStreak) // Streak updated
        })
    }

    @Test
    fun `invoke handles FREQUENCY recurrence correctly`() = runTest {
        val frequencyTask = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_WEEK,
            completionsThisPeriod = 0,
            currentPeriodStart = 0L
        )
        val completionTime = createTimestamp(2024, 1, 15)
        whenever(taskRepository.getTaskById(1L)).thenReturn(frequencyTask)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = completionTime)

        verify(taskRepository).updateTask(check { task ->
            assertFalse(task.isCompleted) // Not yet complete (1 of 3)
            assertEquals(1, task.completionsThisPeriod)
            assertTrue(task.currentPeriodStart > 0)
            assertEquals(1, task.currentStreak)
        })
    }

    @Test
    fun `invoke handles FREQUENCY reaching target correctly`() = runTest {
        val periodStart = createTimestamp(2024, 1, 14)
        val frequencyTask = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_WEEK,
            completionsThisPeriod = 2, // Already 2
            currentPeriodStart = periodStart
        )
        val completionTime = createTimestamp(2024, 1, 16) // Same week
        whenever(taskRepository.getTaskById(1L)).thenReturn(frequencyTask)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = completionTime)

        verify(taskRepository).updateTask(check { task ->
            assertTrue(task.isCompleted) // Now complete (3 of 3)
            assertEquals(3, task.completionsThisPeriod)
        })
    }

    // ========== Consecutive Streak Tests ==========

    @Test
    fun `invoke consecutive day completion increments streak`() = runTest {
        val day1 = createTimestamp(2024, 1, 15)
        val day2 = createTimestamp(2024, 1, 16)

        // First completion
        val taskAfterDay1 = baseTask.copy(
            currentStreak = 1,
            longestStreak = 1,
            lastStreakDate = getStartOfDay(day1)
        )
        whenever(taskRepository.getTaskById(1L)).thenReturn(taskAfterDay1)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = day2)

        verify(taskRepository).updateTask(check { task ->
            assertEquals(2, task.currentStreak)
            assertEquals(2, task.longestStreak)
        })
    }

    @Test
    fun `invoke gap in completions resets streak`() = runTest {
        val day1 = createTimestamp(2024, 1, 15)
        val day3 = createTimestamp(2024, 1, 17) // Gap

        val taskWithStreak = baseTask.copy(
            currentStreak = 5,
            longestStreak = 10,
            lastStreakDate = getStartOfDay(day1)
        )
        whenever(taskRepository.getTaskById(1L)).thenReturn(taskWithStreak)

        completeTaskUseCase.invoke(taskId = 1L, completionTime = day3)

        verify(taskRepository).updateTask(check { task ->
            assertEquals(1, task.currentStreak) // Reset
            assertEquals(10, task.longestStreak) // Preserved
        })
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenThrow(RuntimeException("Database error"))

        val result = completeTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to complete task") == true)
    }

    @Test
    fun `invoke handles update failure gracefully`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)
        whenever(taskRepository.updateTask(any())).thenThrow(RuntimeException("Update failed"))

        val result = completeTaskUseCase.invoke(taskId = 1L)

        assertTrue(result.isFailure)
    }

    // ========== completeWithMetadata Tests ==========

    @Test
    fun `completeWithMetadata delegates to main invoke`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)

        val result = completeTaskUseCase.completeWithMetadata(
            taskId = 1L,
            timeSpent = 30,
            difficulty = 3,
            notes = "Test notes"
        )

        assertTrue(result.isSuccess)
        verify(taskRepository).updateTask(any())
    }

    @Test
    fun `completeWithMetadata with null metadata succeeds`() = runTest {
        whenever(taskRepository.getTaskById(1L)).thenReturn(baseTask)

        val result = completeTaskUseCase.completeWithMetadata(taskId = 1L)

        assertTrue(result.isSuccess)
    }

    // ========== Helper Methods ==========

    private fun createTimestamp(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
