package com.secretary.features.tasks.domain.service

import com.secretary.Task
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for StreakService
 * Phase 4.5.7: Testing & Documentation
 *
 * Tests all streak calculation logic including:
 * - First completion
 * - Consecutive days
 * - Same day multiple completions
 * - Gaps (streak reset)
 * - Longest streak tracking
 * - Streak history calculation
 *
 * Target: 70%+ code coverage
 */
class StreakServiceTest {

    private lateinit var streakService: StreakService
    private lateinit var baseTask: Task

    @Before
    fun setUp() {
        streakService = StreakService()
        baseTask = Task(
            id = 1L,
            title = "Test Task",
            currentStreak = 0,
            longestStreak = 0,
            lastStreakDate = 0L
        )
    }

    // ========== First Completion Tests ==========

    @Test
    fun `updateStreak first completion initializes streak to 1`() {
        val completionTime = createTimestamp(2024, 1, 15)
        val result = streakService.updateStreak(baseTask, completionTime)

        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(getStartOfDay(completionTime), result.lastStreakDate)
    }

    @Test
    fun `updateStreak first completion preserves existing longest streak`() {
        val taskWithHistory = baseTask.copy(longestStreak = 10)
        val completionTime = createTimestamp(2024, 1, 15)

        val result = streakService.updateStreak(taskWithHistory, completionTime)

        assertEquals(1, result.currentStreak)
        assertEquals(10, result.longestStreak) // Preserved
    }

    // ========== Consecutive Day Tests ==========

    @Test
    fun `updateStreak consecutive day increments streak`() {
        val day1 = createTimestamp(2024, 1, 15)
        val day2 = createTimestamp(2024, 1, 16) // Next day

        // First completion
        val afterDay1 = streakService.updateStreak(baseTask, day1)
        assertEquals(1, afterDay1.currentStreak)

        // Second completion (consecutive)
        val afterDay2 = streakService.updateStreak(afterDay1, day2)
        assertEquals(2, afterDay2.currentStreak)
        assertEquals(2, afterDay2.longestStreak)
    }

    @Test
    fun `updateStreak multiple consecutive days build streak`() {
        var task = baseTask
        val startDate = createTimestamp(2024, 1, 1)

        // Complete task 7 days in a row
        for (i in 0 until 7) {
            val completionTime = startDate + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }

        assertEquals(7, task.currentStreak)
        assertEquals(7, task.longestStreak)
    }

    // ========== Same Day Tests ==========

    @Test
    fun `updateStreak same day completion does not change streak`() {
        val day1Morning = createTimestamp(2024, 1, 15, 9, 0)
        val day1Evening = createTimestamp(2024, 1, 15, 21, 0)

        // First completion
        val afterMorning = streakService.updateStreak(baseTask, day1Morning)
        assertEquals(1, afterMorning.currentStreak)

        // Second completion same day
        val afterEvening = streakService.updateStreak(afterMorning, day1Evening)
        assertEquals(1, afterEvening.currentStreak) // No change
        assertEquals(1, afterEvening.longestStreak)
    }

    @Test
    fun `updateStreak same day at midnight boundary handled correctly`() {
        val day1End = createTimestamp(2024, 1, 15, 23, 59)
        val day2Start = createTimestamp(2024, 1, 15, 0, 1)

        val afterFirst = streakService.updateStreak(baseTask, day1End)
        val afterSecond = streakService.updateStreak(afterFirst, day2Start)

        // Both are same day (Jan 15), so streak should not increment
        assertEquals(1, afterSecond.currentStreak)
    }

    // ========== Gap Detection Tests ==========

    @Test
    fun `updateStreak gap of one day resets streak to 1`() {
        val day1 = createTimestamp(2024, 1, 15)
        val day3 = createTimestamp(2024, 1, 17) // Skip day 16

        // First completion
        val afterDay1 = streakService.updateStreak(baseTask, day1)
        assertEquals(1, afterDay1.currentStreak)

        // Completion after gap
        val afterDay3 = streakService.updateStreak(afterDay1, day3)
        assertEquals(1, afterDay3.currentStreak) // Reset
        assertEquals(1, afterDay3.longestStreak)
    }

    @Test
    fun `updateStreak gap preserves longest streak`() {
        var task = baseTask
        val startDate = createTimestamp(2024, 1, 1)

        // Build streak of 5
        for (i in 0 until 5) {
            val completionTime = startDate + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }
        assertEquals(5, task.currentStreak)
        assertEquals(5, task.longestStreak)

        // Gap of 3 days
        val afterGap = createTimestamp(2024, 1, 9) // Jan 6 was last, now Jan 9
        task = streakService.updateStreak(task, afterGap)

        assertEquals(1, task.currentStreak) // Reset
        assertEquals(5, task.longestStreak) // Preserved
    }

    // ========== Longest Streak Tests ==========

    @Test
    fun `updateStreak longest streak tracks maximum ever achieved`() {
        var task = baseTask
        val startDate = createTimestamp(2024, 1, 1)

        // Build streak of 10
        for (i in 0 until 10) {
            val completionTime = startDate + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }
        assertEquals(10, task.longestStreak)

        // Gap - reset current streak
        val afterGap = createTimestamp(2024, 1, 15)
        task = streakService.updateStreak(task, afterGap)
        assertEquals(1, task.currentStreak)
        assertEquals(10, task.longestStreak) // Still 10

        // Build new streak of 5 (less than 10)
        for (i in 1..5) {
            val completionTime = afterGap + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }
        assertEquals(6, task.currentStreak) // 1 + 5
        assertEquals(10, task.longestStreak) // Still 10
    }

    @Test
    fun `updateStreak longest streak updates when current exceeds it`() {
        var task = baseTask
        val startDate = createTimestamp(2024, 1, 1)

        // Build streak of 5
        for (i in 0 until 5) {
            val completionTime = startDate + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }
        assertEquals(5, task.longestStreak)

        // Continue to 10
        for (i in 5 until 10) {
            val completionTime = startDate + (i * 24 * 60 * 60 * 1000L)
            task = streakService.updateStreak(task, completionTime)
        }
        assertEquals(10, task.currentStreak)
        assertEquals(10, task.longestStreak) // Updated
    }

    // ========== calculateStreakFromHistory Tests ==========

    @Test
    fun `calculateStreakFromHistory empty list returns zero streaks`() {
        val result = streakService.calculateStreakFromHistory(emptyList())
        assertEquals(Pair(0, 0), result)
    }

    @Test
    fun `calculateStreakFromHistory single completion returns 1-1`() {
        val completions = listOf(createTimestamp(2024, 1, 15))
        val result = streakService.calculateStreakFromHistory(completions)
        assertEquals(Pair(1, 1), result)
    }

    @Test
    fun `calculateStreakFromHistory consecutive completions calculates correctly`() {
        val completions = listOf(
            createTimestamp(2024, 1, 15),
            createTimestamp(2024, 1, 16),
            createTimestamp(2024, 1, 17),
            createTimestamp(2024, 1, 18)
        )
        val result = streakService.calculateStreakFromHistory(completions)
        assertEquals(Pair(4, 4), result)
    }

    @Test
    fun `calculateStreakFromHistory gap in history detected`() {
        val completions = listOf(
            createTimestamp(2024, 1, 15),
            createTimestamp(2024, 1, 16),
            createTimestamp(2024, 1, 16), // Duplicate same day
            createTimestamp(2024, 1, 20), // Gap
            createTimestamp(2024, 1, 21)
        )
        val result = streakService.calculateStreakFromHistory(completions)

        // Current streak is 2 (Jan 20-21), longest is 2 (Jan 15-16)
        assertEquals(2, result.first) // Current
        assertEquals(2, result.second) // Longest
    }

    @Test
    fun `calculateStreakFromHistory unordered list handled correctly`() {
        val completions = listOf(
            createTimestamp(2024, 1, 18),
            createTimestamp(2024, 1, 15),
            createTimestamp(2024, 1, 17),
            createTimestamp(2024, 1, 16)
        )
        val result = streakService.calculateStreakFromHistory(completions)
        assertEquals(Pair(4, 4), result) // Should be sorted internally
    }

    @Test
    fun `calculateStreakFromHistory multiple same-day completions deduplicated`() {
        val completions = listOf(
            createTimestamp(2024, 1, 15, 9, 0),
            createTimestamp(2024, 1, 15, 12, 0),
            createTimestamp(2024, 1, 15, 18, 0),
            createTimestamp(2024, 1, 16)
        )
        val result = streakService.calculateStreakFromHistory(completions)
        assertEquals(Pair(2, 2), result) // Only 2 unique days
    }

    @Test
    fun `calculateStreakFromHistory complex history with multiple gaps`() {
        val completions = listOf(
            // First streak: 5 days (Jan 1-5)
            createTimestamp(2024, 1, 1),
            createTimestamp(2024, 1, 2),
            createTimestamp(2024, 1, 3),
            createTimestamp(2024, 1, 4),
            createTimestamp(2024, 1, 5),
            // Gap
            // Second streak: 3 days (Jan 10-12)
            createTimestamp(2024, 1, 10),
            createTimestamp(2024, 1, 11),
            createTimestamp(2024, 1, 12),
            // Gap
            // Third streak: 2 days (Jan 20-21) - current
            createTimestamp(2024, 1, 20),
            createTimestamp(2024, 1, 21)
        )
        val result = streakService.calculateStreakFromHistory(completions)

        // Current is 2 (most recent), longest is 5
        assertEquals(2, result.first)
        assertEquals(5, result.second)
    }

    // ========== Edge Cases ==========

    @Test
    fun `updateStreak handles year boundary correctly`() {
        val dec31 = createTimestamp(2023, 12, 31)
        val jan1 = createTimestamp(2024, 1, 1)

        val afterDec31 = streakService.updateStreak(baseTask, dec31)
        val afterJan1 = streakService.updateStreak(afterDec31, jan1)

        assertEquals(2, afterJan1.currentStreak) // Consecutive across year boundary
    }

    @Test
    fun `updateStreak handles leap year february correctly`() {
        val feb28 = createTimestamp(2024, 2, 28) // 2024 is leap year
        val feb29 = createTimestamp(2024, 2, 29)
        val mar1 = createTimestamp(2024, 3, 1)

        var task = baseTask
        task = streakService.updateStreak(task, feb28)
        task = streakService.updateStreak(task, feb29)
        task = streakService.updateStreak(task, mar1)

        assertEquals(3, task.currentStreak) // All consecutive
    }

    // ========== Helper Methods ==========

    private fun createTimestamp(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0) // month is 0-indexed
        cal.set(Calendar.SECOND, 0)
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
