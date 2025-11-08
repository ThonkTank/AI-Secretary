package com.aisecretary.taskmaster.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * CompletionHistoryDao - Data Access Object for CompletionHistory operations
 *
 * Provides CRUD operations for CompletionHistoryEntity.
 * Tracks historical task completions for analysis and statistics.
 * Phase 3.2: Erledigungs-Zeit Tracking
 */
public class CompletionHistoryDao extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "taskmaster.db";
    private static final int DATABASE_VERSION = 2; // Incremented for new table
    private static final String TABLE_COMPLETION_HISTORY = "completion_history";
    private static final String TABLE_TASKS = "tasks";

    // CompletionHistory column names
    private static final String COL_ID = "id";
    private static final String COL_TASK_ID = "task_id";
    private static final String COL_COMPLETED_AT = "completed_at";
    private static final String COL_COMPLETION_TIME = "completion_time";
    private static final String COL_DIFFICULTY_RATING = "difficulty_rating";
    private static final String COL_TIME_OF_DAY = "time_of_day";

    // Task table columns (for onCreate compatibility)
    private static final String TASK_COL_ID = "id";
    private static final String TASK_COL_TITLE = "title";
    private static final String TASK_COL_DESCRIPTION = "description";
    private static final String TASK_COL_PRIORITY = "priority";
    private static final String TASK_COL_COMPLETED = "completed";
    private static final String TASK_COL_CREATED_AT = "created_at";
    private static final String TASK_COL_COMPLETED_AT = "completed_at";
    private static final String TASK_COL_DUE_AT = "due_at";
    private static final String TASK_COL_COMPLETION_COUNT = "completion_count";
    private static final String TASK_COL_LAST_COMPLETED_AT = "last_completed_at";
    private static final String TASK_COL_IS_RECURRING = "is_recurring";
    private static final String TASK_COL_RECURRENCE_TYPE = "recurrence_type";
    private static final String TASK_COL_RECURRENCE_X = "recurrence_x";
    private static final String TASK_COL_RECURRENCE_Y = "recurrence_y";
    private static final String TASK_COL_RECURRENCE_START_DATE = "recurrence_start_date";
    private static final String TASK_COL_RECURRENCE_END_DATE = "recurrence_end_date";
    private static final String TASK_COL_AVG_COMPLETION_TIME = "avg_completion_time";
    private static final String TASK_COL_AVG_DIFFICULTY = "avg_difficulty";
    private static final String TASK_COL_CURRENT_STREAK = "current_streak";
    private static final String TASK_COL_LONGEST_STREAK = "longest_streak";
    private static final String TASK_COL_STREAK_LAST_UPDATED = "streak_last_updated";
    private static final String TASK_COL_PREFERRED_TIME_OF_DAY = "preferred_time_of_day";
    private static final String TASK_COL_PREFERRED_HOUR = "preferred_hour";
    private static final String TASK_COL_CHAIN_ID = "chain_id";
    private static final String TASK_COL_CHAIN_ORDER = "chain_order";
    private static final String TASK_COL_CATEGORY = "category";
    private static final String TASK_COL_OVERDUE_SINCE = "overdue_since";

    public CompletionHistoryDao(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create tasks table (same as TaskDao)
        String CREATE_TASKS_TABLE = "CREATE TABLE " + TABLE_TASKS + " ("
                + TASK_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + TASK_COL_TITLE + " TEXT NOT NULL, "
                + TASK_COL_DESCRIPTION + " TEXT, "
                + TASK_COL_PRIORITY + " INTEGER DEFAULT 2, "
                + TASK_COL_COMPLETED + " INTEGER DEFAULT 0, "
                + TASK_COL_CREATED_AT + " INTEGER, "
                + TASK_COL_COMPLETED_AT + " INTEGER DEFAULT 0, "
                + TASK_COL_DUE_AT + " INTEGER DEFAULT 0, "
                + TASK_COL_COMPLETION_COUNT + " INTEGER DEFAULT 0, "
                + TASK_COL_LAST_COMPLETED_AT + " INTEGER DEFAULT 0, "
                + TASK_COL_IS_RECURRING + " INTEGER DEFAULT 0, "
                + TASK_COL_RECURRENCE_TYPE + " TEXT DEFAULT 'once', "
                + TASK_COL_RECURRENCE_X + " INTEGER DEFAULT 0, "
                + TASK_COL_RECURRENCE_Y + " TEXT, "
                + TASK_COL_RECURRENCE_START_DATE + " INTEGER DEFAULT 0, "
                + TASK_COL_RECURRENCE_END_DATE + " INTEGER DEFAULT 0, "
                + TASK_COL_AVG_COMPLETION_TIME + " INTEGER DEFAULT 0, "
                + TASK_COL_AVG_DIFFICULTY + " REAL DEFAULT 0, "
                + TASK_COL_CURRENT_STREAK + " INTEGER DEFAULT 0, "
                + TASK_COL_LONGEST_STREAK + " INTEGER DEFAULT 0, "
                + TASK_COL_STREAK_LAST_UPDATED + " INTEGER DEFAULT 0, "
                + TASK_COL_PREFERRED_TIME_OF_DAY + " TEXT, "
                + TASK_COL_PREFERRED_HOUR + " INTEGER DEFAULT 0, "
                + TASK_COL_CHAIN_ID + " INTEGER DEFAULT 0, "
                + TASK_COL_CHAIN_ORDER + " INTEGER DEFAULT 0, "
                + TASK_COL_CATEGORY + " TEXT, "
                + TASK_COL_OVERDUE_SINCE + " INTEGER DEFAULT 0"
                + ")";

        // Create completion_history table
        String CREATE_COMPLETION_HISTORY_TABLE = "CREATE TABLE " + TABLE_COMPLETION_HISTORY + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TASK_ID + " INTEGER NOT NULL, "
                + COL_COMPLETED_AT + " INTEGER NOT NULL, "
                + COL_COMPLETION_TIME + " INTEGER DEFAULT 0, "
                + COL_DIFFICULTY_RATING + " REAL DEFAULT 0, "
                + COL_TIME_OF_DAY + " INTEGER DEFAULT 0, "
                + "FOREIGN KEY(" + COL_TASK_ID + ") REFERENCES " + TABLE_TASKS + "(" + TASK_COL_ID + ") ON DELETE CASCADE"
                + ")";

        db.execSQL(CREATE_TASKS_TABLE);
        db.execSQL(CREATE_COMPLETION_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add completion_history table
            String CREATE_COMPLETION_HISTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_COMPLETION_HISTORY + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_TASK_ID + " INTEGER NOT NULL, "
                    + COL_COMPLETED_AT + " INTEGER NOT NULL, "
                    + COL_COMPLETION_TIME + " INTEGER DEFAULT 0, "
                    + COL_DIFFICULTY_RATING + " REAL DEFAULT 0, "
                    + COL_TIME_OF_DAY + " INTEGER DEFAULT 0, "
                    + "FOREIGN KEY(" + COL_TASK_ID + ") REFERENCES " + TABLE_TASKS + "(" + TASK_COL_ID + ") ON DELETE CASCADE"
                    + ")";
            db.execSQL(CREATE_COMPLETION_HISTORY_TABLE);
        }
    }

    /**
     * Insert a new completion history entry
     */
    public long insert(CompletionHistoryEntity entry) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = entryToContentValues(entry);
        long id = db.insert(TABLE_COMPLETION_HISTORY, null, values);
        // Don't close db - it's a cached singleton instance
        return id;
    }

    /**
     * Get all completion history entries for a specific task
     */
    public List<CompletionHistoryEntity> getByTaskId(long taskId) {
        List<CompletionHistoryEntity> history = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_COMPLETION_HISTORY + " WHERE "
                + COL_TASK_ID + " = ? ORDER BY " + COL_COMPLETED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        if (cursor.moveToFirst()) {
            do {
                history.add(cursorToEntry(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return history;
    }

    /**
     * Get recent completion history entries (last N entries)
     */
    public List<CompletionHistoryEntity> getRecentByTaskId(long taskId, int limit) {
        List<CompletionHistoryEntity> history = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_COMPLETION_HISTORY + " WHERE "
                + COL_TASK_ID + " = ? ORDER BY " + COL_COMPLETED_AT + " DESC LIMIT ?";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId), String.valueOf(limit)});

        if (cursor.moveToFirst()) {
            do {
                history.add(cursorToEntry(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return history;
    }

    /**
     * Get completion history for a date range
     */
    public List<CompletionHistoryEntity> getByDateRange(long taskId, long startTime, long endTime) {
        List<CompletionHistoryEntity> history = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_COMPLETION_HISTORY + " WHERE "
                + COL_TASK_ID + " = ? AND "
                + COL_COMPLETED_AT + " >= ? AND "
                + COL_COMPLETED_AT + " < ? "
                + "ORDER BY " + COL_COMPLETED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{
                String.valueOf(taskId),
                String.valueOf(startTime),
                String.valueOf(endTime)
        });

        if (cursor.moveToFirst()) {
            do {
                history.add(cursorToEntry(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return history;
    }

    /**
     * Calculate average completion time for a task
     */
    public long getAverageCompletionTime(long taskId) {
        String query = "SELECT AVG(" + COL_COMPLETION_TIME + ") FROM " + TABLE_COMPLETION_HISTORY + " WHERE "
                + COL_TASK_ID + " = ? AND " + COL_COMPLETION_TIME + " > 0";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        long average = 0;
        if (cursor.moveToFirst()) {
            average = cursor.getLong(0);
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return average;
    }

    /**
     * Calculate average difficulty rating for a task
     */
    public float getAverageDifficulty(long taskId) {
        String query = "SELECT AVG(" + COL_DIFFICULTY_RATING + ") FROM " + TABLE_COMPLETION_HISTORY + " WHERE "
                + COL_TASK_ID + " = ? AND " + COL_DIFFICULTY_RATING + " > 0";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        float average = 0;
        if (cursor.moveToFirst()) {
            average = cursor.getFloat(0);
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return average;
    }

    /**
     * Get most common time of day for task completion
     */
    public int getMostCommonTimeOfDay(long taskId) {
        String query = "SELECT " + COL_TIME_OF_DAY + ", COUNT(*) as count FROM " + TABLE_COMPLETION_HISTORY
                + " WHERE " + COL_TASK_ID + " = ? "
                + "GROUP BY " + COL_TIME_OF_DAY + " "
                + "ORDER BY count DESC LIMIT 1";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        int mostCommonHour = -1;
        if (cursor.moveToFirst()) {
            mostCommonHour = cursor.getInt(0);
        }
        cursor.close();
        // Don't close db - it's a cached singleton instance
        return mostCommonHour;
    }

    /**
     * Delete all history entries for a task
     */
    public void deleteByTaskId(long taskId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_COMPLETION_HISTORY, COL_TASK_ID + " = ?", new String[]{String.valueOf(taskId)});
        // Don't close db - it's a cached singleton instance
    }

    /**
     * Convert CompletionHistoryEntity to ContentValues
     */
    private ContentValues entryToContentValues(CompletionHistoryEntity entry) {
        ContentValues values = new ContentValues();
        values.put(COL_TASK_ID, entry.taskId);
        values.put(COL_COMPLETED_AT, entry.completedAt);
        values.put(COL_COMPLETION_TIME, entry.completionTime);
        values.put(COL_DIFFICULTY_RATING, entry.difficultyRating);
        values.put(COL_TIME_OF_DAY, entry.timeOfDay);
        return values;
    }

    /**
     * Convert Cursor to CompletionHistoryEntity
     */
    private CompletionHistoryEntity cursorToEntry(Cursor cursor) {
        CompletionHistoryEntity entry = new CompletionHistoryEntity();
        entry.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
        entry.taskId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TASK_ID));
        entry.completedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPLETED_AT));
        entry.completionTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPLETION_TIME));
        entry.difficultyRating = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_DIFFICULTY_RATING));
        entry.timeOfDay = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TIME_OF_DAY));
        return entry;
    }
}
