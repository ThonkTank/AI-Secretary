package com.secretary.features.tasks.domain.service

import com.secretary.Task
import java.util.Calendar

/**
 * RecurrenceService - Domain Service for Recurring Task Logic
 * Phase 4.5.3 Wave 10 Step 3: Recurrence Logic Extraction
 *
 * Pure domain service with NO database operations or Android dependencies.
 * Handles all recurrence-related calculations and state transitions.
 *
 * Supports two recurrence types:
 * - INTERVAL: "Every X Y" (e.g., every 3 days) - resets when time elapses
 * - FREQUENCY: "X times per Y" (e.g., 3 times per week) - tracks completions within period
 */
class RecurrenceService {

    companion object {
        private const val TAG = "RecurrenceService"
    }

    /**
     * Handle recurring task completion.
     * Returns updated Task with new recurrence state.
     *
     * @param task The task being completed
     * @param completionTime The timestamp of completion (default: now)
     * @return Updated task with recurrence logic applied
     */
    fun handleRecurringCompletion(task: Task, completionTime: Long = System.currentTimeMillis()): Task {
        return when (task.recurrenceType) {
            Task.RECURRENCE_INTERVAL -> handleIntervalCompletion(task, completionTime)
            Task.RECURRENCE_FREQUENCY -> handleFrequencyCompletion(task, completionTime)
            else -> task.copy(
                isCompleted = true,
                lastCompletedDate = completionTime
            )
        }
    }

    /**
     * Handle INTERVAL recurrence completion.
     * Sets task as completed and calculates next due date.
     */
    private fun handleIntervalCompletion(task: Task, completionTime: Long): Task {
        val nextDueDate = calculateNextDueDate(
            currentDueDate = completionTime,
            amount = task.recurrenceAmount,
            unit = task.recurrenceUnit
        )

        return task.copy(
            isCompleted = true,
            lastCompletedDate = completionTime,
            dueDate = nextDueDate
        )
    }

    /**
     * Handle FREQUENCY recurrence completion.
     * Increments completion counter and marks complete if target reached.
     */
    private fun handleFrequencyCompletion(task: Task, completionTime: Long): Task {
        var completions = task.completionsThisPeriod
        var periodStart = task.currentPeriodStart

        // Check if we're in a new period
        if (!isInCurrentPeriod(periodStart, task.recurrenceUnit, completionTime)) {
            // New period - reset counters
            completions = 0
            periodStart = getPeriodStart(completionTime, task.recurrenceUnit)
        }

        // Increment completion count
        completions++

        // Check if target reached
        val isCompleted = completions >= task.recurrenceAmount

        return task.copy(
            isCompleted = isCompleted,
            completionsThisPeriod = completions,
            currentPeriodStart = periodStart,
            lastCompletedDate = completionTime
        )
    }

    /**
     * Calculate the next due date based on recurrence settings.
     *
     * @param currentDueDate The current due date (or completion time)
     * @param amount The recurrence amount (e.g., 3 for "every 3 days")
     * @param unit The time unit (DAY, WEEK, MONTH, YEAR)
     * @return Timestamp of next due date
     */
    fun calculateNextDueDate(currentDueDate: Long, amount: Int, unit: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentDueDate

        when (unit) {
            Task.UNIT_DAY -> cal.add(Calendar.DAY_OF_MONTH, amount)
            Task.UNIT_WEEK -> cal.add(Calendar.WEEK_OF_YEAR, amount)
            Task.UNIT_MONTH -> cal.add(Calendar.MONTH, amount)
            Task.UNIT_YEAR -> cal.add(Calendar.YEAR, amount)
        }

        return cal.timeInMillis
    }

    /**
     * Check if a timestamp is in the current period.
     *
     * @param periodStart The start of the period being checked
     * @param unit The period unit (DAY, WEEK, MONTH, YEAR)
     * @param now The current timestamp
     * @return true if timestamp is in current period
     */
    fun isInCurrentPeriod(periodStart: Long, unit: Int, now: Long): Boolean {
        if (periodStart == 0L) return false

        val periodCal = Calendar.getInstance()
        periodCal.timeInMillis = periodStart

        val nowCal = Calendar.getInstance()
        nowCal.timeInMillis = now

        return when (unit) {
            Task.UNIT_DAY -> {
                periodCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                periodCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
            }
            Task.UNIT_WEEK -> {
                periodCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                periodCal.get(Calendar.WEEK_OF_YEAR) == nowCal.get(Calendar.WEEK_OF_YEAR)
            }
            Task.UNIT_MONTH -> {
                periodCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                periodCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
            }
            Task.UNIT_YEAR -> {
                periodCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
            }
            else -> false
        }
    }

    /**
     * Get the start of the current period for a given timestamp.
     *
     * @param timestamp The timestamp to get period start for
     * @param unit The period unit (DAY, WEEK, MONTH, YEAR)
     * @return Timestamp at start of period (00:00:00)
     */
    fun getPeriodStart(timestamp: Long, unit: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp

        // Reset to start of day
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (unit) {
            Task.UNIT_DAY -> {
                // Already at start of day
            }
            Task.UNIT_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            }
            Task.UNIT_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
            Task.UNIT_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return cal.timeInMillis
    }

    /**
     * Check if an INTERVAL task should be reset (due date reached).
     *
     * @param task The task to check
     * @param currentTime Current timestamp
     * @return true if task should be reset
     */
    fun shouldResetIntervalTask(task: Task, currentTime: Long): Boolean {
        return task.recurrenceType == Task.RECURRENCE_INTERVAL &&
               task.isCompleted &&
               task.dueDate > 0 &&
               task.dueDate <= currentTime
    }

    /**
     * Check if a FREQUENCY task should be reset (new period started).
     *
     * @param task The task to check
     * @param currentTime Current timestamp
     * @return true if task should be reset
     */
    fun shouldResetFrequencyTask(task: Task, currentTime: Long): Boolean {
        return task.recurrenceType == Task.RECURRENCE_FREQUENCY &&
               task.currentPeriodStart > 0 &&
               !isInCurrentPeriod(task.currentPeriodStart, task.recurrenceUnit, currentTime)
    }

    /**
     * Reset an INTERVAL task to uncompleted state.
     * Clears due date until next completion.
     */
    fun resetIntervalTask(task: Task): Task {
        return task.copy(
            isCompleted = false,
            dueDate = 0 // Clear due date until next completion
        )
    }

    /**
     * Reset a FREQUENCY task for a new period.
     * Resets counters and period start.
     */
    fun resetFrequencyTask(task: Task, newPeriodStart: Long): Task {
        return task.copy(
            isCompleted = false,
            completionsThisPeriod = 0,
            currentPeriodStart = newPeriodStart
        )
    }

    /**
     * Get all tasks that need to be reset (both INTERVAL and FREQUENCY).
     * Returns a list of task IDs and their updated states.
     *
     * @param tasks List of all tasks to check
     * @param currentTime Current timestamp (default: now)
     * @return Map of task ID to updated Task
     */
    fun getTasksNeedingReset(tasks: List<Task>, currentTime: Long = System.currentTimeMillis()): Map<Long, Task> {
        val updates = mutableMapOf<Long, Task>()

        for (task in tasks) {
            when {
                shouldResetIntervalTask(task, currentTime) -> {
                    updates[task.id] = resetIntervalTask(task)
                }
                shouldResetFrequencyTask(task, currentTime) -> {
                    val newPeriodStart = getPeriodStart(currentTime, task.recurrenceUnit)
                    updates[task.id] = resetFrequencyTask(task, newPeriodStart)
                }
            }
        }

        return updates
    }
}
