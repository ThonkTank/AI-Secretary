package com.secretary.helloworld

/**
 * Task entity representing a single task in the Taskmaster system.
 * Phase 4.5.3 Wave 2: Converted to Kotlin
 *
 * Comprehensive task model with support for:
 * - Basic task information (title, description, category)
 * - Priority levels (Low, Medium, High, Urgent)
 * - Due dates and completion tracking
 * - Recurring tasks (INTERVAL and FREQUENCY patterns)
 * - Streak tracking for consistency
 *
 * NOTE: Properties are currently 'var' (mutable) instead of 'val' (immutable)
 * to maintain compatibility with TaskDatabaseHelper.java which uses setters.
 * This will be reverted to 'val' in Wave 5 when TaskDatabaseHelper is converted
 * to Kotlin with Room ORM using builder pattern.
 */
data class Task(
    var id: Long = 0,
    var title: String = "",
    var description: String? = null,
    var category: String = "General",
    var createdAt: Long = System.currentTimeMillis(),
    var dueDate: Long = 0,
    var isCompleted: Boolean = false,
    var priority: Int = 1, // 0=Low, 1=Medium, 2=High, 3=Urgent

    // Recurrence fields
    var recurrenceType: Int = RecurrenceType.NONE,
    var recurrenceAmount: Int = 0, // The "X" in both patterns
    var recurrenceUnit: Int = TimeUnit.DAY, // The "Y" time unit
    var lastCompletedDate: Long = 0, // For tracking
    var completionsThisPeriod: Int = 0, // For FREQUENCY type tracking
    var currentPeriodStart: Long = 0, // When current period started

    // Streak tracking
    var currentStreak: Int = 0, // Current consecutive completions
    var longestStreak: Int = 0, // Best streak ever
    var lastStreakDate: Long = 0 // Last date counted for streak
) {
    companion object {
        // Recurrence Types
        object RecurrenceType {
            const val NONE = 0
            const val INTERVAL = 1 // "Every X Y" (e.g., every 3 days)
            const val FREQUENCY = 2 // "X times per Y" (e.g., 3 times per week)
        }

        // Time Units for Recurrence
        object TimeUnit {
            const val DAY = 0
            const val WEEK = 1
            const val MONTH = 2
            const val YEAR = 3
        }

        // Backward compatibility with Java code (to be removed in Wave 5)
        const val RECURRENCE_NONE = RecurrenceType.NONE
        const val RECURRENCE_INTERVAL = RecurrenceType.INTERVAL
        const val RECURRENCE_FREQUENCY = RecurrenceType.FREQUENCY
        const val UNIT_DAY = TimeUnit.DAY
        const val UNIT_WEEK = TimeUnit.WEEK
        const val UNIT_MONTH = TimeUnit.MONTH
        const val UNIT_YEAR = TimeUnit.YEAR
    }

    /**
     * Get human-readable priority string
     */
    fun getPriorityString(): String = when (priority) {
        0 -> "Low"
        2 -> "High"
        3 -> "Urgent"
        else -> "Medium"
    }

    /**
     * Get human-readable recurrence pattern string
     */
    fun getRecurrenceString(): String {
        if (recurrenceType == RecurrenceType.NONE) {
            return "No repeat"
        }

        val unitStr = getUnitString(recurrenceUnit)

        return when (recurrenceType) {
            RecurrenceType.INTERVAL -> {
                // "Every X Y" format
                if (recurrenceAmount == 1) {
                    "Every $unitStr"
                } else {
                    "Every $recurrenceAmount ${unitStr}s"
                }
            }
            RecurrenceType.FREQUENCY -> {
                // "X times per Y" format
                if (recurrenceAmount == 1) {
                    "Once per $unitStr"
                } else {
                    "$recurrenceAmount times per $unitStr"
                }
            }
            else -> "Unknown recurrence"
        }
    }

    /**
     * Get human-readable time unit string
     */
    private fun getUnitString(unit: Int): String = when (unit) {
        TimeUnit.DAY -> "day"
        TimeUnit.WEEK -> "week"
        TimeUnit.MONTH -> "month"
        TimeUnit.YEAR -> "year"
        else -> "unknown"
    }

    /**
     * Check if this task has recurrence enabled
     */
    fun isRecurring(): Boolean = recurrenceType != RecurrenceType.NONE

    /**
     * Get progress string for frequency-based recurring tasks
     * Returns empty string for non-frequency tasks
     */
    fun getProgressString(): String {
        if (recurrenceType != RecurrenceType.FREQUENCY) {
            return ""
        }
        return "($completionsThisPeriod/$recurrenceAmount)"
    }

    /**
     * Get next appearance info for completed interval tasks
     * Returns human-readable time until task reappears
     */
    fun getNextAppearanceString(): String {
        if (recurrenceType != RecurrenceType.INTERVAL || !isCompleted || dueDate <= 0) {
            return ""
        }

        val now = System.currentTimeMillis()
        val timeUntilDue = dueDate - now

        if (timeUntilDue <= 0) {
            return " (due now)"
        }

        // Convert to readable format
        val hours = timeUntilDue / (1000 * 60 * 60)
        val days = hours / 24

        return when {
            days > 0 -> " (reappears in $days day${if (days > 1) "s" else ""})"
            hours > 0 -> " (reappears in $hours hour${if (hours > 1) "s" else ""})"
            else -> {
                val minutes = timeUntilDue / (1000 * 60)
                " (reappears in $minutes minute${if (minutes > 1) "s" else ""})"
            }
        }
    }

    /**
     * Check if this frequency task needs more completions in current period
     */
    fun needsMoreCompletions(): Boolean {
        if (recurrenceType != RecurrenceType.FREQUENCY) {
            return false
        }
        return completionsThisPeriod < recurrenceAmount
    }

    /**
     * String representation showing title and completion status
     */
    override fun toString(): String = "$title${if (isCompleted) " ✓" else ""}"
}
