package com.secretary.helloworld.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.data.database.TaskDatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Task Completion history.
 * Handles all database operations related to task completions.
 */
public class CompletionDao {
    private static final String TAG = "CompletionDao";

    private final TaskDatabaseHelper dbHelper;
    private final AppLogger logger;

    // Table and column names
    private static final String TABLE_COMPLETIONS = "completions";
    private static final String COLUMN_COMPLETION_ID = "completion_id";
    private static final String COLUMN_TASK_ID = "task_id";
    private static final String COLUMN_COMPLETED_AT = "completed_at";
    private static final String COLUMN_TIME_SPENT = "time_spent_minutes";
    private static final String COLUMN_DIFFICULTY = "difficulty";
    private static final String COLUMN_NOTES = "notes";

    public CompletionDao(Context context) {
        this.dbHelper = new TaskDatabaseHelper(context);
        this.logger = AppLogger.getInstance(context);
    }

    /**
     * Record a task completion
     */
    public long recordCompletion(long taskId, int timeSpent, int difficulty, String notes) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TASK_ID, taskId);
        values.put(COLUMN_COMPLETED_AT, System.currentTimeMillis());
        values.put(COLUMN_TIME_SPENT, timeSpent);
        values.put(COLUMN_DIFFICULTY, difficulty);
        values.put(COLUMN_NOTES, notes);

        long id = db.insert(TABLE_COMPLETIONS, null, values);
        logger.info(TAG, "Completion recorded for task " + taskId + " with ID: " + id);

        return id;
    }

    /**
     * Get average completion time for a task
     */
    public int getAverageCompletionTime(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT AVG(" + COLUMN_TIME_SPENT + ") FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ? AND " + COLUMN_TIME_SPENT + " > 0";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        int avgTime = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                avgTime = cursor.getInt(0);
            }
            cursor.close();
        }

        return avgTime;
    }

    /**
     * Get completion count for a task
     */
    public int getCompletionCount(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT COUNT(*) FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }

        return count;
    }

    /**
     * Get average difficulty for a task
     */
    public float getAverageDifficulty(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT AVG(" + COLUMN_DIFFICULTY + ") FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ? AND " + COLUMN_DIFFICULTY + " > 0";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        float avgDifficulty = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                avgDifficulty = cursor.getFloat(0);
            }
            cursor.close();
        }

        return avgDifficulty;
    }

    /**
     * Get all completions for a task
     */
    public List<TaskCompletion> getCompletionsForTask(long taskId) {
        List<TaskCompletion> completions = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(TABLE_COMPLETIONS, null,
                COLUMN_TASK_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null,
                COLUMN_COMPLETED_AT + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                completions.add(cursorToCompletion(cursor));
            }
            cursor.close();
        }

        return completions;
    }

    /**
     * Get total time spent on a task
     */
    public int getTotalTimeSpent(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT SUM(" + COLUMN_TIME_SPENT + ") FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(taskId)});

        int totalTime = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                totalTime = cursor.getInt(0);
            }
            cursor.close();
        }

        return totalTime;
    }

    /**
     * Get total completed tasks count
     */
    public int getTotalCompletedTasks() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT COUNT(DISTINCT " + COLUMN_TASK_ID + ") FROM " + TABLE_COMPLETIONS;

        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }

        return count;
    }

    /**
     * Get completions in date range
     */
    public int getCompletionsInRange(long startTime, long endTime) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT COUNT(*) FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_COMPLETED_AT + " >= ? AND " +
                      COLUMN_COMPLETED_AT + " <= ?";

        Cursor cursor = db.rawQuery(query,
                new String[]{String.valueOf(startTime), String.valueOf(endTime)});

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }

        return count;
    }

    /**
     * Delete completions for a task
     */
    public int deleteCompletionsForTask(long taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rowsDeleted = db.delete(TABLE_COMPLETIONS,
                COLUMN_TASK_ID + " = ?",
                new String[]{String.valueOf(taskId)});

        logger.info(TAG, "Deleted " + rowsDeleted + " completions for task " + taskId);

        return rowsDeleted;
    }

    /**
     * Convert Cursor to TaskCompletion object
     */
    private TaskCompletion cursorToCompletion(Cursor cursor) {
        TaskCompletion completion = new TaskCompletion();

        completion.completionId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETION_ID));
        completion.taskId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TASK_ID));
        completion.completedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT));
        completion.timeSpentMinutes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIME_SPENT));
        completion.difficulty = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DIFFICULTY));
        completion.notes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES));

        return completion;
    }

    /**
     * Close database connection
     */
    public void close() {
        dbHelper.close();
    }

    /**
     * Inner class representing a task completion
     */
    public static class TaskCompletion {
        public long completionId;
        public long taskId;
        public long completedAt;
        public int timeSpentMinutes;
        public int difficulty;
        public String notes;
    }
}