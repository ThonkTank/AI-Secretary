package com.aisecretary.taskmaster.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskDao - Data Access Object for Task operations
 *
 * Provides CRUD operations for TaskEntity.
 * Simplified implementation without Room annotation processing.
 */
public class TaskDao extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "taskmaster.db";
    private static final int DATABASE_VERSION = 2; // Updated for completion_history table
    private static final String TABLE_TASKS = "tasks";
    private static final String TABLE_COMPLETION_HISTORY = "completion_history";

    // Column names
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_COMPLETED_AT = "completed_at";
    private static final String COL_DUE_AT = "due_at";
    private static final String COL_COMPLETION_COUNT = "completion_count";
    private static final String COL_LAST_COMPLETED_AT = "last_completed_at";
    private static final String COL_IS_RECURRING = "is_recurring";
    private static final String COL_RECURRENCE_TYPE = "recurrence_type";
    private static final String COL_RECURRENCE_X = "recurrence_x";
    private static final String COL_RECURRENCE_Y = "recurrence_y";
    private static final String COL_RECURRENCE_START_DATE = "recurrence_start_date";
    private static final String COL_RECURRENCE_END_DATE = "recurrence_end_date";
    private static final String COL_AVG_COMPLETION_TIME = "avg_completion_time";
    private static final String COL_AVG_DIFFICULTY = "avg_difficulty";
    private static final String COL_CURRENT_STREAK = "current_streak";
    private static final String COL_LONGEST_STREAK = "longest_streak";
    private static final String COL_STREAK_LAST_UPDATED = "streak_last_updated";
    private static final String COL_PREFERRED_TIME_OF_DAY = "preferred_time_of_day";
    private static final String COL_PREFERRED_HOUR = "preferred_hour";
    private static final String COL_CHAIN_ID = "chain_id";
    private static final String COL_CHAIN_ORDER = "chain_order";
    private static final String COL_CATEGORY = "category";
    private static final String COL_OVERDUE_SINCE = "overdue_since";

    public TaskDao(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TASKS_TABLE = "CREATE TABLE " + TABLE_TASKS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_PRIORITY + " INTEGER DEFAULT 2, "
                + COL_COMPLETED + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_COMPLETED_AT + " INTEGER DEFAULT 0, "
                + COL_DUE_AT + " INTEGER DEFAULT 0, "
                + COL_COMPLETION_COUNT + " INTEGER DEFAULT 0, "
                + COL_LAST_COMPLETED_AT + " INTEGER DEFAULT 0, "
                + COL_IS_RECURRING + " INTEGER DEFAULT 0, "
                + COL_RECURRENCE_TYPE + " TEXT DEFAULT 'once', "
                + COL_RECURRENCE_X + " INTEGER DEFAULT 0, "
                + COL_RECURRENCE_Y + " TEXT, "
                + COL_RECURRENCE_START_DATE + " INTEGER DEFAULT 0, "
                + COL_RECURRENCE_END_DATE + " INTEGER DEFAULT 0, "
                + COL_AVG_COMPLETION_TIME + " INTEGER DEFAULT 0, "
                + COL_AVG_DIFFICULTY + " REAL DEFAULT 0, "
                + COL_CURRENT_STREAK + " INTEGER DEFAULT 0, "
                + COL_LONGEST_STREAK + " INTEGER DEFAULT 0, "
                + COL_STREAK_LAST_UPDATED + " INTEGER DEFAULT 0, "
                + COL_PREFERRED_TIME_OF_DAY + " TEXT, "
                + COL_PREFERRED_HOUR + " INTEGER DEFAULT 0, "
                + COL_CHAIN_ID + " INTEGER DEFAULT 0, "
                + COL_CHAIN_ORDER + " INTEGER DEFAULT 0, "
                + COL_CATEGORY + " TEXT, "
                + COL_OVERDUE_SINCE + " INTEGER DEFAULT 0"
                + ")";

        String CREATE_COMPLETION_HISTORY_TABLE = "CREATE TABLE " + TABLE_COMPLETION_HISTORY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "task_id INTEGER NOT NULL, "
                + "completed_at INTEGER NOT NULL, "
                + "completion_time INTEGER DEFAULT 0, "
                + "difficulty_rating REAL DEFAULT 0, "
                + "time_of_day INTEGER DEFAULT 0, "
                + "FOREIGN KEY(task_id) REFERENCES " + TABLE_TASKS + "(" + COL_ID + ") ON DELETE CASCADE"
                + ")";

        db.execSQL(CREATE_TASKS_TABLE);
        db.execSQL(CREATE_COMPLETION_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add completion_history table for Phase 3.2
            String CREATE_COMPLETION_HISTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_COMPLETION_HISTORY + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "task_id INTEGER NOT NULL, "
                    + "completed_at INTEGER NOT NULL, "
                    + "completion_time INTEGER DEFAULT 0, "
                    + "difficulty_rating REAL DEFAULT 0, "
                    + "time_of_day INTEGER DEFAULT 0, "
                    + "FOREIGN KEY(task_id) REFERENCES " + TABLE_TASKS + "(" + COL_ID + ") ON DELETE CASCADE"
                    + ")";
            db.execSQL(CREATE_COMPLETION_HISTORY_TABLE);
        }
    }

    /**
     * Insert a new task
     */
    public long insert(TaskEntity task) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = taskToContentValues(task);
        long id = db.insert(TABLE_TASKS, null, values);
        db.close();
        return id;
    }

    /**
     * Update an existing task
     */
    public int update(TaskEntity task) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = taskToContentValues(task);
        int rowsAffected = db.update(TABLE_TASKS, values, COL_ID + " = ?",
                new String[]{String.valueOf(task.id)});
        db.close();
        return rowsAffected;
    }

    /**
     * Delete a task
     */
    public void delete(long taskId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TASKS, COL_ID + " = ?", new String[]{String.valueOf(taskId)});
        db.close();
    }

    /**
     * Get task by ID
     */
    public TaskEntity getById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TASKS, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        TaskEntity task = null;
        if (cursor != null && cursor.moveToFirst()) {
            task = cursorToTask(cursor);
            cursor.close();
        }
        db.close();
        return task;
    }

    /**
     * Get all tasks
     */
    public List<TaskEntity> getAll() {
        List<TaskEntity> tasks = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_TASKS + " ORDER BY " + COL_CREATED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                tasks.add(cursorToTask(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return tasks;
    }

    /**
     * Get tasks for today (due today or overdue)
     */
    public List<TaskEntity> getTasksForToday() {
        List<TaskEntity> tasks = new ArrayList<>();
        long now = System.currentTimeMillis();
        long dayStart = now - (now % 86400000);
        long dayEnd = dayStart + 86400000;

        String query = "SELECT * FROM " + TABLE_TASKS + " WHERE "
                + COL_COMPLETED + " = 0 AND ("
                + "(" + COL_DUE_AT + " >= " + dayStart + " AND " + COL_DUE_AT + " < " + dayEnd + ") OR "
                + COL_DUE_AT + " < " + dayStart + ") "
                + "ORDER BY " + COL_PRIORITY + " DESC, " + COL_DUE_AT + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                tasks.add(cursorToTask(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return tasks;
    }

    /**
     * Get overdue tasks
     */
    public List<TaskEntity> getOverdueTasks() {
        List<TaskEntity> tasks = new ArrayList<>();
        long now = System.currentTimeMillis();

        String query = "SELECT * FROM " + TABLE_TASKS + " WHERE "
                + COL_COMPLETED + " = 0 AND "
                + COL_DUE_AT + " > 0 AND "
                + COL_DUE_AT + " < " + now + " "
                + "ORDER BY " + COL_DUE_AT + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                tasks.add(cursorToTask(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return tasks;
    }

    /**
     * Get tasks with active streaks
     */
    public List<TaskEntity> getTasksWithStreaks() {
        List<TaskEntity> tasks = new ArrayList<>();

        String query = "SELECT * FROM " + TABLE_TASKS + " WHERE "
                + COL_CURRENT_STREAK + " > 0 "
                + "ORDER BY " + COL_CURRENT_STREAK + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                tasks.add(cursorToTask(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return tasks;
    }

    /**
     * Get completed tasks count for date range
     */
    public int getCompletedCount(long startTime, long endTime) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_TASKS + " WHERE "
                + COL_COMPLETED_AT + " >= " + startTime + " AND "
                + COL_COMPLETED_AT + " < " + endTime;

        Cursor cursor = db.rawQuery(query, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }

    /**
     * Convert Task to ContentValues for database operations
     */
    private ContentValues taskToContentValues(TaskEntity task) {
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, task.title);
        values.put(COL_DESCRIPTION, task.description);
        values.put(COL_PRIORITY, task.priority);
        values.put(COL_COMPLETED, task.completed ? 1 : 0);
        values.put(COL_CREATED_AT, task.createdAt);
        values.put(COL_COMPLETED_AT, task.completedAt);
        values.put(COL_DUE_AT, task.dueAt);
        values.put(COL_COMPLETION_COUNT, task.completionCount);
        values.put(COL_LAST_COMPLETED_AT, task.lastCompletedAt);
        values.put(COL_IS_RECURRING, task.isRecurring ? 1 : 0);
        values.put(COL_RECURRENCE_TYPE, task.recurrenceType);
        values.put(COL_RECURRENCE_X, task.recurrenceX);
        values.put(COL_RECURRENCE_Y, task.recurrenceY);
        values.put(COL_RECURRENCE_START_DATE, task.recurrenceStartDate);
        values.put(COL_RECURRENCE_END_DATE, task.recurrenceEndDate);
        values.put(COL_AVG_COMPLETION_TIME, task.averageCompletionTime);
        values.put(COL_AVG_DIFFICULTY, task.averageDifficulty);
        values.put(COL_CURRENT_STREAK, task.currentStreak);
        values.put(COL_LONGEST_STREAK, task.longestStreak);
        values.put(COL_STREAK_LAST_UPDATED, task.streakLastUpdated);
        values.put(COL_PREFERRED_TIME_OF_DAY, task.preferredTimeOfDay);
        values.put(COL_PREFERRED_HOUR, task.preferredHour);
        values.put(COL_CHAIN_ID, task.chainId);
        values.put(COL_CHAIN_ORDER, task.chainOrder);
        values.put(COL_CATEGORY, task.category);
        values.put(COL_OVERDUE_SINCE, task.overdueSince);
        return values;
    }

    /**
     * Convert Cursor to TaskEntity
     */
    private TaskEntity cursorToTask(Cursor cursor) {
        TaskEntity task = new TaskEntity();
        task.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
        task.title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        task.description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION));
        task.priority = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRIORITY));
        task.completed = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;
        task.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        task.completedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_COMPLETED_AT));
        task.dueAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DUE_AT));
        task.completionCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETION_COUNT));
        task.lastCompletedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_COMPLETED_AT));
        task.isRecurring = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_RECURRING)) == 1;
        task.recurrenceType = cursor.getString(cursor.getColumnIndexOrThrow(COL_RECURRENCE_TYPE));
        task.recurrenceX = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECURRENCE_X));
        task.recurrenceY = cursor.getString(cursor.getColumnIndexOrThrow(COL_RECURRENCE_Y));
        task.recurrenceStartDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECURRENCE_START_DATE));
        task.recurrenceEndDate = cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECURRENCE_END_DATE));
        task.averageCompletionTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_AVG_COMPLETION_TIME));
        task.averageDifficulty = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_AVG_DIFFICULTY));
        task.currentStreak = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CURRENT_STREAK));
        task.longestStreak = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LONGEST_STREAK));
        task.streakLastUpdated = cursor.getLong(cursor.getColumnIndexOrThrow(COL_STREAK_LAST_UPDATED));
        task.preferredTimeOfDay = cursor.getString(cursor.getColumnIndexOrThrow(COL_PREFERRED_TIME_OF_DAY));
        task.preferredHour = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PREFERRED_HOUR));
        task.chainId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CHAIN_ID));
        task.chainOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHAIN_ORDER));
        task.category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY));
        task.overdueSince = cursor.getLong(cursor.getColumnIndexOrThrow(COL_OVERDUE_SINCE));
        return task;
    }
}
