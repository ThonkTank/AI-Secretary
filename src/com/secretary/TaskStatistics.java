package com.secretary.helloworld;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.secretary.helloworld.DatabaseConstants.*;

/**
 * Helper class for task statistics and analytics.
 * Extracted from TaskDatabaseHelper for better organization.
 */
public class TaskStatistics {
    private final SQLiteDatabase database;

    public TaskStatistics(SQLiteDatabase database) {
        this.database = database;
    }

    /**
     * Get total task count
     */
    public int getTaskCount() {
        Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + TABLE_TASKS, null);
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
     * Get count of tasks completed today
     */
    public int getTasksCompletedToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long todayStart = calendar.getTimeInMillis();

        String query = "SELECT COUNT(*) FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_COMPLETED_AT + " >= ?";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(todayStart)});
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
     * Get count of tasks completed in last 7 days
     */
    public int getTasksCompletedLast7Days() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long weekAgo = calendar.getTimeInMillis();

        String query = "SELECT COUNT(*) FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_COMPLETED_AT + " >= ?";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(weekAgo)});
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
     * Get count of overdue tasks
     */
    public int getOverdueTasksCount() {
        long currentTime = System.currentTimeMillis();
        String query = "SELECT COUNT(*) FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_DUE_DATE + " > 0" +
                      " AND " + COLUMN_DUE_DATE + " < ?" +
                      " AND " + COLUMN_IS_COMPLETED + " = 0";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(currentTime)});
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
     * Get average completion time for a task
     */
    public int getAverageCompletionTime(long taskId) {
        String query = "SELECT AVG(" + COLUMN_TIME_SPENT + ") FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ? AND " + COLUMN_TIME_SPENT + " > 0";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(taskId)});
        int avgTime = 0;
        if (cursor != null) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                avgTime = cursor.getInt(0);
            }
            cursor.close();
        }
        return avgTime;
    }

    /**
     * Get task completion history
     */
    public List<ContentValues> getTaskCompletionHistory(long taskId) {
        List<ContentValues> history = new ArrayList<>();

        String query = "SELECT * FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = ?" +
                      " ORDER BY " + COLUMN_COMPLETED_AT + " DESC";

        Cursor cursor = database.rawQuery(query, new String[]{String.valueOf(taskId)});

        if (cursor != null) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_COMPLETION_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETION_ID)));
                values.put(COLUMN_TASK_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TASK_ID)));
                values.put(COLUMN_COMPLETED_AT, cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_AT)));
                values.put(COLUMN_TIME_SPENT, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TIME_SPENT)));
                values.put(COLUMN_DIFFICULTY, cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DIFFICULTY)));
                values.put(COLUMN_NOTES, cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES)));
                history.add(values);
            }
            cursor.close();
        }

        return history;
    }
}