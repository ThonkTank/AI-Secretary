package com.secretary.features.tasks.domain.service

import com.secretary.Task
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for RecurrenceService
 * Phase 4.5.7: Testing & Documentation
 *
 * Tests all recurrence logic including:
 * - INTERVAL recurrence (every X days/weeks/months)
 * - FREQUENCY recurrence (X times per period)
 * - Period boundary detection
 * - Task reset logic
 * - Next due date calculation
 *
 * Target: 70%+ code coverage
 */
class RecurrenceServiceTest {

    private lateinit var recurrenceService: RecurrenceService
    private lateinit var baseTask: Task

    @Before
    fun setUp() {
        recurrenceService = RecurrenceService()
        baseTask = Task(
            id = 1L,
            title = "Test Task",
            recurrenceType = Task.RECURRENCE_NONE,
            recurrenceAmount = 0,
            recurrenceUnit = Task.UNIT_DAY,
            isCompleted = false,
            lastCompletedDate = 0L
        )
    }

    // ========== Non-Recurring Task Tests ==========

    @Test
    fun `handleRecurringCompletion non-recurring task marks as completed`() {
        val completionTime = createTimestamp(2024, 1, 15)
        val result = recurrenceService.handleRecurringCompletion(baseTask, completionTime)

        assertTrue(result.isCompleted)
        assertEquals(completionTime, result.lastCompletedDate)
    }

    // ========== INTERVAL Recurrence Tests ==========

    @Test
    fun `handleRecurringCompletion INTERVAL daily sets next due date`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 1,
            recurrenceUnit = Task.UNIT_DAY
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        assertTrue(result.isCompleted)
        assertEquals(completionTime, result.lastCompletedDate)

        // Next due date should be 1 day later
        val expectedNextDue = createTimestamp(2024, 1, 16)
        assertEquals(expectedNextDue, result.dueDate)
    }

    @Test
    fun `handleRecurringCompletion INTERVAL every 3 days calculates correctly`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_DAY
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        val expectedNextDue = createTimestamp(2024, 1, 18) // 3 days later
        assertEquals(expectedNextDue, result.dueDate)
    }

    @Test
    fun `handleRecurringCompletion INTERVAL weekly sets next due date`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 1,
            recurrenceUnit = Task.UNIT_WEEK
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        val expectedNextDue = createTimestamp(2024, 1, 22) // 7 days later
        assertEquals(expectedNextDue, result.dueDate)
    }

    @Test
    fun `handleRecurringCompletion INTERVAL monthly sets next due date`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 1,
            recurrenceUnit = Task.UNIT_MONTH
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        val expectedNextDue = createTimestamp(2024, 2, 15) // 1 month later
        assertEquals(expectedNextDue, result.dueDate)
    }

    @Test
    fun `handleRecurringCompletion INTERVAL yearly sets next due date`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            recurrenceAmount = 1,
            recurrenceUnit = Task.UNIT_YEAR
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        val expectedNextDue = createTimestamp(2025, 1, 15) // 1 year later
        assertEquals(expectedNextDue, result.dueDate)
    }

    // ========== FREQUENCY Recurrence Tests ==========

    @Test
    fun `handleRecurringCompletion FREQUENCY first completion in period`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 3, // 3 times per week
            recurrenceUnit = Task.UNIT_WEEK,
            completionsThisPeriod = 0,
            currentPeriodStart = 0L
        )
        val completionTime = createTimestamp(2024, 1, 15)

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        assertFalse(result.isCompleted) // Not yet complete (1 of 3)
        assertEquals(1, result.completionsThisPeriod)
        assertTrue(result.currentPeriodStart > 0) // Period initialized
    }

    @Test
    fun `handleRecurringCompletion FREQUENCY reaches target completes task`() {
        val periodStart = createTimestamp(2024, 1, 14) // Week start
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_WEEK,
            completionsThisPeriod = 2, // Already 2 completions
            currentPeriodStart = periodStart
        )
        val completionTime = createTimestamp(2024, 1, 16) // Same week

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        assertTrue(result.isCompleted) // 3rd completion - complete!
        assertEquals(3, result.completionsThisPeriod)
    }

    @Test
    fun `handleRecurringCompletion FREQUENCY new period resets counter`() {
        val oldPeriodStart = createTimestamp(2024, 1, 1) // Week 1
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 3,
            recurrenceUnit = Task.UNIT_WEEK,
            completionsThisPeriod = 2,
            currentPeriodStart = oldPeriodStart
        )
        val completionTime = createTimestamp(2024, 1, 15) // Week 3 - new period

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        assertEquals(1, result.completionsThisPeriod) // Reset and incremented
        assertNotEquals(oldPeriodStart, result.currentPeriodStart) // New period start
    }

    @Test
    fun `handleRecurringCompletion FREQUENCY daily 5 times per day`() {
        val periodStart = createTimestamp(2024, 1, 15)
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceAmount = 5,
            recurrenceUnit = Task.UNIT_DAY,
            completionsThisPeriod = 4,
            currentPeriodStart = periodStart
        )
        val completionTime = createTimestamp(2024, 1, 15, 18, 0) // Same day

        val result = recurrenceService.handleRecurringCompletion(task, completionTime)

        assertTrue(result.isCompleted) // 5th completion
        assertEquals(5, result.completionsThisPeriod)
    }

    // ========== calculateNextDueDate Tests ==========

    @Test
    fun `calculateNextDueDate adds days correctly`() {
        val currentDate = createTimestamp(2024, 1, 15)
        val nextDate = recurrenceService.calculateNextDueDate(currentDate, 5, Task.UNIT_DAY)

        val expected = createTimestamp(2024, 1, 20)
        assertEquals(expected, nextDate)
    }

    @Test
    fun `calculateNextDueDate adds weeks correctly`() {
        val currentDate = createTimestamp(2024, 1, 15)
        val nextDate = recurrenceService.calculateNextDueDate(currentDate, 2, Task.UNIT_WEEK)

        val expected = createTimestamp(2024, 1, 29)
        assertEquals(expected, nextDate)
    }

    @Test
    fun `calculateNextDueDate adds months correctly`() {
        val currentDate = createTimestamp(2024, 1, 15)
        val nextDate = recurrenceService.calculateNextDueDate(currentDate, 3, Task.UNIT_MONTH)

        val expected = createTimestamp(2024, 4, 15)
        assertEquals(expected, nextDate)
    }

    @Test
    fun `calculateNextDueDate handles month boundary correctly`() {
        val currentDate = createTimestamp(2024, 1, 31)
        val nextDate = recurrenceService.calculateNextDueDate(currentDate, 1, Task.UNIT_MONTH)

        // Jan 31 + 1 month = Feb 29 (2024 is leap year, so last day of Feb)
        val cal = Calendar.getInstance()
        cal.timeInMillis = nextDate
        assertEquals(2, cal.get(Calendar.MONTH)) // February (0-indexed)
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `calculateNextDueDate adds years correctly`() {
        val currentDate = createTimestamp(2024, 1, 15)
        val nextDate = recurrenceService.calculateNextDueDate(currentDate, 2, Task.UNIT_YEAR)

        val expected = createTimestamp(2026, 1, 15)
        assertEquals(expected, nextDate)
    }

    // ========== isInCurrentPeriod Tests ==========

    @Test
    fun `isInCurrentPeriod returns false for zero period start`() {
        val now = createTimestamp(2024, 1, 15)
        assertFalse(recurrenceService.isInCurrentPeriod(0L, Task.UNIT_DAY, now))
    }

    @Test
    fun `isInCurrentPeriod DAY same day returns true`() {
        val periodStart = createTimestamp(2024, 1, 15, 8, 0)
        val now = createTimestamp(2024, 1, 15, 18, 0)
        assertTrue(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_DAY, now))
    }

    @Test
    fun `isInCurrentPeriod DAY different day returns false`() {
        val periodStart = createTimestamp(2024, 1, 15)
        val now = createTimestamp(2024, 1, 16)
        assertFalse(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_DAY, now))
    }

    @Test
    fun `isInCurrentPeriod WEEK same week returns true`() {
        val periodStart = createTimestamp(2024, 1, 15) // Monday
        val now = createTimestamp(2024, 1, 18) // Thursday same week
        assertTrue(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_WEEK, now))
    }

    @Test
    fun `isInCurrentPeriod WEEK different week returns false`() {
        val periodStart = createTimestamp(2024, 1, 15) // Week 3
        val now = createTimestamp(2024, 1, 22) // Week 4
        assertFalse(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_WEEK, now))
    }

    @Test
    fun `isInCurrentPeriod MONTH same month returns true`() {
        val periodStart = createTimestamp(2024, 1, 5)
        val now = createTimestamp(2024, 1, 25)
        assertTrue(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_MONTH, now))
    }

    @Test
    fun `isInCurrentPeriod MONTH different month returns false`() {
        val periodStart = createTimestamp(2024, 1, 15)
        val now = createTimestamp(2024, 2, 15)
        assertFalse(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_MONTH, now))
    }

    @Test
    fun `isInCurrentPeriod YEAR same year returns true`() {
        val periodStart = createTimestamp(2024, 1, 1)
        val now = createTimestamp(2024, 12, 31)
        assertTrue(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_YEAR, now))
    }

    @Test
    fun `isInCurrentPeriod YEAR different year returns false`() {
        val periodStart = createTimestamp(2024, 1, 1)
        val now = createTimestamp(2025, 1, 1)
        assertFalse(recurrenceService.isInCurrentPeriod(periodStart, Task.UNIT_YEAR, now))
    }

    // ========== getPeriodStart Tests ==========

    @Test
    fun `getPeriodStart DAY returns start of day`() {
        val timestamp = createTimestamp(2024, 1, 15, 14, 30)
        val periodStart = recurrenceService.getPeriodStart(timestamp, Task.UNIT_DAY)

        val expected = createTimestamp(2024, 1, 15, 0, 0)
        assertEquals(expected, periodStart)
    }

    @Test
    fun `getPeriodStart WEEK returns start of week`() {
        val timestamp = createTimestamp(2024, 1, 18) // Thursday
        val periodStart = recurrenceService.getPeriodStart(timestamp, Task.UNIT_WEEK)

        val cal = Calendar.getInstance()
        cal.timeInMillis = periodStart
        assertEquals(cal.firstDayOfWeek, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `getPeriodStart MONTH returns start of month`() {
        val timestamp = createTimestamp(2024, 1, 18)
        val periodStart = recurrenceService.getPeriodStart(timestamp, Task.UNIT_MONTH)

        val expected = createTimestamp(2024, 1, 1, 0, 0)
        assertEquals(expected, periodStart)
    }

    @Test
    fun `getPeriodStart YEAR returns start of year`() {
        val timestamp = createTimestamp(2024, 6, 15)
        val periodStart = recurrenceService.getPeriodStart(timestamp, Task.UNIT_YEAR)

        val expected = createTimestamp(2024, 1, 1, 0, 0)
        assertEquals(expected, periodStart)
    }

    // ========== Task Reset Tests ==========

    @Test
    fun `shouldResetIntervalTask returns true when due date reached`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            isCompleted = true,
            dueDate = createTimestamp(2024, 1, 15)
        )
        val currentTime = createTimestamp(2024, 1, 16) // Past due date

        assertTrue(recurrenceService.shouldResetIntervalTask(task, currentTime))
    }

    @Test
    fun `shouldResetIntervalTask returns false when due date not reached`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            isCompleted = true,
            dueDate = createTimestamp(2024, 1, 20)
        )
        val currentTime = createTimestamp(2024, 1, 15) // Before due date

        assertFalse(recurrenceService.shouldResetIntervalTask(task, currentTime))
    }

    @Test
    fun `shouldResetIntervalTask returns false when not completed`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_INTERVAL,
            isCompleted = false,
            dueDate = createTimestamp(2024, 1, 15)
        )
        val currentTime = createTimestamp(2024, 1, 16)

        assertFalse(recurrenceService.shouldResetIntervalTask(task, currentTime))
    }

    @Test
    fun `shouldResetFrequencyTask returns true when new period started`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceUnit = Task.UNIT_WEEK,
            currentPeriodStart = createTimestamp(2024, 1, 8) // Week 2
        )
        val currentTime = createTimestamp(2024, 1, 22) // Week 4

        assertTrue(recurrenceService.shouldResetFrequencyTask(task, currentTime))
    }

    @Test
    fun `shouldResetFrequencyTask returns false when in same period`() {
        val task = baseTask.copy(
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceUnit = Task.UNIT_WEEK,
            currentPeriodStart = createTimestamp(2024, 1, 15)
        )
        val currentTime = createTimestamp(2024, 1, 18) // Same week

        assertFalse(recurrenceService.shouldResetFrequencyTask(task, currentTime))
    }

    @Test
    fun `resetIntervalTask clears completion and due date`() {
        val task = baseTask.copy(
            isCompleted = true,
            dueDate = createTimestamp(2024, 1, 15)
        )

        val result = recurrenceService.resetIntervalTask(task)

        assertFalse(result.isCompleted)
        assertEquals(0L, result.dueDate)
    }

    @Test
    fun `resetFrequencyTask clears completion and resets counters`() {
        val task = baseTask.copy(
            isCompleted = true,
            completionsThisPeriod = 5,
            currentPeriodStart = createTimestamp(2024, 1, 1)
        )
        val newPeriodStart = createTimestamp(2024, 2, 1)

        val result = recurrenceService.resetFrequencyTask(task, newPeriodStart)

        assertFalse(result.isCompleted)
        assertEquals(0, result.completionsThisPeriod)
        assertEquals(newPeriodStart, result.currentPeriodStart)
    }

    // ========== getTasksNeedingReset Tests ==========

    @Test
    fun `getTasksNeedingReset returns empty map when no resets needed`() {
        val tasks = listOf(
            baseTask.copy(id = 1L, recurrenceType = Task.RECURRENCE_NONE),
            baseTask.copy(id = 2L, recurrenceType = Task.RECURRENCE_INTERVAL, isCompleted = false)
        )

        val result = recurrenceService.getTasksNeedingReset(tasks)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTasksNeedingReset identifies INTERVAL task needing reset`() {
        val task1 = baseTask.copy(
            id = 1L,
            recurrenceType = Task.RECURRENCE_INTERVAL,
            isCompleted = true,
            dueDate = createTimestamp(2024, 1, 10)
        )
        val currentTime = createTimestamp(2024, 1, 15) // Past due date

        val result = recurrenceService.getTasksNeedingReset(listOf(task1), currentTime)

        assertEquals(1, result.size)
        assertTrue(result.containsKey(1L))
        assertFalse(result[1L]!!.isCompleted)
    }

    @Test
    fun `getTasksNeedingReset identifies FREQUENCY task needing reset`() {
        val task1 = baseTask.copy(
            id = 1L,
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceUnit = Task.UNIT_WEEK,
            currentPeriodStart = createTimestamp(2024, 1, 1),
            completionsThisPeriod = 3
        )
        val currentTime = createTimestamp(2024, 1, 15) // New week

        val result = recurrenceService.getTasksNeedingReset(listOf(task1), currentTime)

        assertEquals(1, result.size)
        assertEquals(0, result[1L]!!.completionsThisPeriod)
    }

    @Test
    fun `getTasksNeedingReset handles multiple tasks`() {
        val task1 = baseTask.copy(
            id = 1L,
            recurrenceType = Task.RECURRENCE_INTERVAL,
            isCompleted = true,
            dueDate = createTimestamp(2024, 1, 10)
        )
        val task2 = baseTask.copy(
            id = 2L,
            recurrenceType = Task.RECURRENCE_FREQUENCY,
            recurrenceUnit = Task.UNIT_WEEK,
            currentPeriodStart = createTimestamp(2024, 1, 1)
        )
        val task3 = baseTask.copy(
            id = 3L,
            recurrenceType = Task.RECURRENCE_NONE
        )
        val currentTime = createTimestamp(2024, 1, 20)

        val result = recurrenceService.getTasksNeedingReset(listOf(task1, task2, task3), currentTime)

        assertEquals(2, result.size) // task1 and task2 need reset
        assertTrue(result.containsKey(1L))
        assertTrue(result.containsKey(2L))
        assertFalse(result.containsKey(3L))
    }

    // ========== Helper Methods ==========

    private fun createTimestamp(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
