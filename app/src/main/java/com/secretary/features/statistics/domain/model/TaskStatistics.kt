package com.secretary.features.statistics.domain.model

/**
 * Domain model for aggregated task statistics.
 * Phase 4: Motivation & Statistics
 *
 * Pure domain model with NO Android or Room dependencies.
 * Used by GetStatisticsUseCase and presentation layer.
 *
 * @property completedToday Number of tasks completed today (since 00:00:00)
 * @property completedThisWeek Number of tasks completed in the last 7 days
 * @property activeTasks Number of active (uncompleted) tasks
 * @property totalTasks Total number of all tasks
 */
data class TaskStatistics(
    val completedToday: Int,
    val completedThisWeek: Int,
    val activeTasks: Int,
    val totalTasks: Int
) {
    /**
     * Calculate completion rate for today.
     * @return Percentage of total tasks completed today (0-100)
     */
    fun getTodayCompletionRate(): Int {
        if (totalTasks == 0) return 0
        return ((completedToday.toFloat() / totalTasks) * 100).toInt()
    }

    /**
     * Calculate completion rate for this week.
     * @return Percentage of total tasks completed this week (0-100)
     */
    fun getWeekCompletionRate(): Int {
        if (totalTasks == 0) return 0
        return ((completedThisWeek.toFloat() / totalTasks) * 100).toInt()
    }

    /**
     * Format statistics for display.
     * @return Human-readable statistics string
     */
    fun toDisplayString(): String {
        return "Today: $completedToday | Week: $completedThisWeek | Active: $activeTasks"
    }
}
