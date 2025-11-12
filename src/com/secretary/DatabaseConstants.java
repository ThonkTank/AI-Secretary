package com.secretary.helloworld;

/**
 * Database constants extracted from TaskDatabaseHelper
 * for better organization and reusability.
 */
public class DatabaseConstants {
    // Database configuration
    public static final String DATABASE_NAME = "taskmaster.db";
    public static final int DATABASE_VERSION = 5;

    // Table names
    public static final String TABLE_TASKS = "tasks";
    public static final String TABLE_COMPLETIONS = "completions";

    // Tasks table columns
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_DUE_DATE = "due_date";
    public static final String COLUMN_IS_COMPLETED = "is_completed";
    public static final String COLUMN_PRIORITY = "priority";

    // Recurrence columns
    public static final String COLUMN_RECURRENCE_TYPE = "recurrence_type";
    public static final String COLUMN_RECURRENCE_AMOUNT = "recurrence_amount";
    public static final String COLUMN_RECURRENCE_UNIT = "recurrence_unit";
    public static final String COLUMN_LAST_COMPLETED_DATE = "last_completed_date";
    public static final String COLUMN_COMPLETIONS_THIS_PERIOD = "completions_this_period";
    public static final String COLUMN_CURRENT_PERIOD_START = "current_period_start";

    // Streak columns
    public static final String COLUMN_CURRENT_STREAK = "current_streak";
    public static final String COLUMN_LONGEST_STREAK = "longest_streak";
    public static final String COLUMN_LAST_STREAK_DATE = "last_streak_date";

    // Completion history table columns
    public static final String COLUMN_COMPLETION_ID = "completion_id";
    public static final String COLUMN_TASK_ID = "task_id";
    public static final String COLUMN_COMPLETED_AT = "completed_at";
    public static final String COLUMN_TIME_SPENT = "time_spent_minutes";
    public static final String COLUMN_DIFFICULTY = "difficulty";
    public static final String COLUMN_NOTES = "notes";

    // Prevent instantiation
    private DatabaseConstants() {}
}