package com.secretary.helloworld.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.secretary.helloworld.AppLogger;

/**
 * Database helper responsible for creating and upgrading the database schema.
 * Business logic has been moved to DAO classes.
 */
public class TaskDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "TaskDatabaseHelper";

    // Database configuration
    private static final String DATABASE_NAME = "taskmaster.db";
    private static final int DATABASE_VERSION = 5;

    private final AppLogger logger;

    // SQL statements for table creation
    private static final String CREATE_TASKS_TABLE =
            "CREATE TABLE IF NOT EXISTS tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "title TEXT NOT NULL, " +
            "description TEXT, " +
            "category TEXT, " +
            "created_at INTEGER NOT NULL, " +
            "due_date INTEGER, " +
            "is_completed INTEGER DEFAULT 0, " +
            "priority INTEGER DEFAULT 0, " +
            "recurrence_type INTEGER DEFAULT 0, " +
            "recurrence_amount INTEGER DEFAULT 0, " +
            "recurrence_unit INTEGER DEFAULT 0, " +
            "last_completed_date INTEGER DEFAULT 0, " +
            "completions_this_period INTEGER DEFAULT 0, " +
            "current_period_start INTEGER DEFAULT 0, " +
            "current_streak INTEGER DEFAULT 0, " +
            "longest_streak INTEGER DEFAULT 0, " +
            "last_streak_date INTEGER DEFAULT 0)";

    private static final String CREATE_COMPLETIONS_TABLE =
            "CREATE TABLE IF NOT EXISTS completions (" +
            "completion_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id INTEGER NOT NULL, " +
            "completed_at INTEGER NOT NULL, " +
            "time_spent_minutes INTEGER DEFAULT 0, " +
            "difficulty INTEGER DEFAULT 0, " +
            "notes TEXT, " +
            "FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE)";

    private static final String CREATE_INDEX_TASK_COMPLETED =
            "CREATE INDEX IF NOT EXISTS idx_task_completed ON tasks(is_completed)";

    private static final String CREATE_INDEX_TASK_CATEGORY =
            "CREATE INDEX IF NOT EXISTS idx_task_category ON tasks(category)";

    private static final String CREATE_INDEX_TASK_DUE_DATE =
            "CREATE INDEX IF NOT EXISTS idx_task_due_date ON tasks(due_date)";

    private static final String CREATE_INDEX_COMPLETION_TASK =
            "CREATE INDEX IF NOT EXISTS idx_completion_task ON completions(task_id)";

    public TaskDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.logger = AppLogger.getInstance(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        logger.info(TAG, "Creating database schema version " + DATABASE_VERSION);

        // Create tables
        db.execSQL(CREATE_TASKS_TABLE);
        db.execSQL(CREATE_COMPLETIONS_TABLE);

        // Create indexes for better performance
        db.execSQL(CREATE_INDEX_TASK_COMPLETED);
        db.execSQL(CREATE_INDEX_TASK_CATEGORY);
        db.execSQL(CREATE_INDEX_TASK_DUE_DATE);
        db.execSQL(CREATE_INDEX_COMPLETION_TASK);

        logger.info(TAG, "Database schema created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        logger.info(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // Version 2: No changes needed (was planned for Room migration)

        // Version 3: Add category field
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT");
            db.execSQL(CREATE_INDEX_TASK_CATEGORY);
            logger.info(TAG, "Added category column to tasks table");
        }

        // Version 4: Add completion tracking
        if (oldVersion < 4) {
            db.execSQL(CREATE_COMPLETIONS_TABLE);
            db.execSQL(CREATE_INDEX_COMPLETION_TASK);
            logger.info(TAG, "Created completions table for tracking");
        }

        // Version 5: Add streak tracking
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN current_streak INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN longest_streak INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN last_streak_date INTEGER DEFAULT 0");
            logger.info(TAG, "Added streak tracking columns");
        }

        logger.info(TAG, "Database upgrade completed");
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true);
    }
}