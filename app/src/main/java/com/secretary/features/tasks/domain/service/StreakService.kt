package com.secretary.features.tasks.domain.service

import com.secretary.Task
import java.util.Calendar

/**
 * StreakService - Domain Service for Streak Management
 * Phase 4.5.3 Wave 10 Step 4: Streak Logic Implementation
 *
 * Pure domain service with NO database operations or Android dependencies.
 * Handles streak calculation and maintenance for task completions.
 *
 * Streak Logic:
 * - Consecutive completions (daily) increment streak
 * - Gaps (skipped days) reset streak to 1
 * - Multiple completions same day don't change streak
 * - Tracks longest streak ever achieved
 */
class StreakService {

    companion object {
        private const val TAG = "StreakService"
    }

    /**
     * Update streak counters based on task completion.
     * Returns updated Task with new streak values.
     *
     * @param task The task being completed
     * @param completionTime The timestamp of completion (default: now)
     * @return Updated task with streak logic applied
     */
    fun updateStreak(task: Task, completionTime: Long = System.currentTimeMillis()): Task {
        val currentDay = getStartOfDay(completionTime)
        val lastStreakDay = if (task.lastStreakDate > 0) getStartOfDay(task.lastStreakDate) else 0L

        val (newCurrentStreak, newLongestStreak) = when {
            // First completion ever - initialize streak
            lastStreakDay == 0L -> {
                Pair(1, maxOf(1, task.longestStreak))
            }

            // Same day - already counted, no change
            isSameDay(lastStreakDay, currentDay) -> {
                Pair(task.currentStreak, task.longestStreak)
            }

            // Consecutive day (yesterday) - increment streak
            isConsecutiveDay(lastStreakDay, currentDay) -> {
                val newCurrent = task.currentStreak + 1
                val newLongest = maxOf(newCurrent, task.longestStreak)
                Pair(newCurrent, newLongest)
            }

            // Gap detected (missed day) - reset streak to 1
            else -> {
                Pair(1, task.longestStreak)
            }
        }

        return task.copy(
            currentStreak = newCurrentStreak,
            longestStreak = newLongestStreak,
            lastStreakDate = currentDay
        )
    }

    /**
     * Check if two timestamps are on the same day.
     *
     * @param timestamp1 First timestamp (normalized to start of day)
     * @param timestamp2 Second timestamp (normalized to start of day)
     * @return true if both are the same day
     */
    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        return timestamp1 == timestamp2
    }

    /**
     * Check if second timestamp is exactly one day after first.
     * Used to detect consecutive daily completions.
     *
     * @param lastDay Previous streak date (normalized to start of day)
     * @param currentDay Current completion date (normalized to start of day)
     * @return true if currentDay is exactly 1 day after lastDay
     */
    private fun isConsecutiveDay(lastDay: Long, currentDay: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastDay
        cal.add(Calendar.DAY_OF_YEAR, 1)

        val expectedNextDay = getStartOfDay(cal.timeInMillis)
        return currentDay == expectedNextDay
    }

    /**
     * Normalize timestamp to start of day (00:00:00.000).
     * Ensures consistent day-based comparisons.
     *
     * @param timestamp The timestamp to normalize
     * @return Timestamp at 00:00:00.000 of the same day
     */
    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Calculate streak for a task based on completion history.
     * This is useful for recalculating streaks from scratch.
     *
     * @param completionDates List of completion timestamps (should be sorted descending)
     * @return Pair of (currentStreak, longestStreak)
     */
    fun calculateStreakFromHistory(completionDates: List<Long>): Pair<Int, Int> {
        if (completionDates.isEmpty()) {
            return Pair(0, 0)
        }

        val normalizedDates = completionDates.map { getStartOfDay(it) }.distinct().sortedDescending()

        var currentStreak = 0
        var longestStreak = 0
        var streakCount = 0
        var lastDate = 0L

        for (date in normalizedDates) {
            when {
                lastDate == 0L -> {
                    // First date
                    streakCount = 1
                    currentStreak = 1
                }
                isConsecutiveDay(date, lastDate) -> {
                    // Consecutive day (going backwards, so date is 1 day before lastDate)
                    streakCount++
                    currentStreak = maxOf(currentStreak, streakCount)
                }
                else -> {
                    // Gap - save current streak and start new
                    longestStreak = maxOf(longestStreak, streakCount)
                    streakCount = 1
                }
            }

            lastDate = date
        }

        // Final streak check
        longestStreak = maxOf(longestStreak, streakCount)

        return Pair(currentStreak, longestStreak)
    }
}
