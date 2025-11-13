package com.secretary.helloworld.shared.database

/**
 * Database constants extracted from TaskDatabaseHelper
 * for better organization and reusability.
 *
 * Converted to Kotlin in Phase 4.5.3 (Kotlin Migration)
 */
object DatabaseConstants {
    // Database configuration
    const val DATABASE_NAME = "taskmaster.db"
    const val DATABASE_VERSION = 5

    // Table names
    const val TABLE_TASKS = "tasks"
    const val TABLE_COMPLETIONS = "completions"

    // Tasks table columns
    const val COLUMN_ID = "id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_DESCRIPTION = "description"
    const val COLUMN_CATEGORY = "category"
    const val COLUMN_CREATED_AT = "created_at"
    const val COLUMN_DUE_DATE = "due_date"
    const val COLUMN_IS_COMPLETED = "is_completed"
    const val COLUMN_PRIORITY = "priority"

    // Recurrence columns
    const val COLUMN_RECURRENCE_TYPE = "recurrence_type"
    const val COLUMN_RECURRENCE_AMOUNT = "recurrence_amount"
    const val COLUMN_RECURRENCE_UNIT = "recurrence_unit"
    const val COLUMN_LAST_COMPLETED_DATE = "last_completed_date"
    const val COLUMN_COMPLETIONS_THIS_PERIOD = "completions_this_period"
    const val COLUMN_CURRENT_PERIOD_START = "current_period_start"

    // Streak columns
    const val COLUMN_CURRENT_STREAK = "current_streak"
    const val COLUMN_LONGEST_STREAK = "longest_streak"
    const val COLUMN_LAST_STREAK_DATE = "last_streak_date"

    // Completion history table columns
    const val COLUMN_COMPLETION_ID = "completion_id"
    const val COLUMN_TASK_ID = "task_id"
    const val COLUMN_COMPLETED_AT = "completed_at"
    const val COLUMN_TIME_SPENT = "time_spent_minutes"
    const val COLUMN_DIFFICULTY = "difficulty"
    const val COLUMN_NOTES = "notes"
}
