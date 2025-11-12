package com.secretary.helloworld.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.secretary.helloworld.Task;
import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.data.database.TaskDatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Task entities.
 * Handles all database operations related to tasks.
 */
public class TaskDao {
    private static final String TAG = "TaskDao";

    private final TaskDatabaseHelper dbHelper;
    private final AppLogger logger;

    // Table and column names
    private static final String TABLE_TASKS = "tasks";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_DUE_DATE = "due_date";
    private static final String COLUMN_IS_COMPLETED = "is_completed";
    private static final String COLUMN_PRIORITY = "priority";
    private static final String COLUMN_RECURRENCE_TYPE = "recurrence_type";
    private static final String COLUMN_RECURRENCE_AMOUNT = "recurrence_amount";
    private static final String COLUMN_RECURRENCE_UNIT = "recurrence_unit";
    private static final String COLUMN_LAST_COMPLETED_DATE = "last_completed_date";
    private static final String COLUMN_COMPLETIONS_THIS_PERIOD = "completions_this_period";
    private static final String COLUMN_CURRENT_PERIOD_START = "current_period_start";
    private static final String COLUMN_CURRENT_STREAK = "current_streak";
    private static final String COLUMN_LONGEST_STREAK = "longest_streak";
    private static final String COLUMN_LAST_STREAK_DATE = "last_streak_date";

    public TaskDao(Context context) {
        this.dbHelper = new TaskDatabaseHelper(context);
        this.logger = AppLogger.getInstance(context);
    }

    /**
     * Insert a new task
     */
    public long insertTask(Task task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = taskToContentValues(task);

        long id = db.insert(TABLE_TASKS, null, values);
        logger.info(TAG, "Task inserted with ID: " + id);

        return id;
    }

    /**
     * Update an existing task
     */
    public int updateTask(Task task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = taskToContentValues(task);

        int rowsAffected = db.update(TABLE_TASKS, values,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(task.getId())});

        logger.info(TAG, "Task updated: " + task.getTitle() + " (Rows affected: " + rowsAffected + ")");

        return rowsAffected;
    }

    /**
     * Delete a task by ID
     */
    public int deleteTask(long taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rowsDeleted = db.delete(TABLE_TASKS,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)});

        logger.info(TAG, "Task deleted with ID: " + taskId);

        return rowsDeleted;
    }

    /**
     * Get a task by ID
     */
    public Task getTask(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TASKS, null,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)},
                null, null, null);

        Task task = null;
        if (cursor != null && cursor.moveToFirst()) {
            task = cursorToTask(cursor);
            cursor.close();
        }

        return task;
    }

    /**
     * Get all tasks
     */
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(TABLE_TASKS, null, null, null, null, null,
                COLUMN_CREATED_AT + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor));
            }
            cursor.close();
        }

        logger.debug(TAG, "Retrieved " + tasks.size() + " tasks from database");

        return tasks;
    }

    /**
     * Get all unique categories
     */
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(true, TABLE_TASKS,
                new String[]{COLUMN_CATEGORY},
                COLUMN_CATEGORY + " IS NOT NULL AND " + COLUMN_CATEGORY + " != ''",
                null, null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String category = cursor.getString(0);
                if (category != null && !category.trim().isEmpty()) {
                    categories.add(category);
                }
            }
            cursor.close();
        }

        return categories;
    }

    /**
     * Mark task as completed or not completed
     */
    public void markTaskCompleted(long taskId, boolean isCompleted) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_COMPLETED, isCompleted ? 1 : 0);

        if (isCompleted) {
            values.put(COLUMN_LAST_COMPLETED_DATE, System.currentTimeMillis());
        }

        db.update(TABLE_TASKS, values,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)});

        logger.info(TAG, "Task " + taskId + " marked as " + (isCompleted ? "completed" : "incomplete"));
    }

    /**
     * Update streak information for a task
     */
    public void updateStreaks(long taskId, int currentStreak, int longestStreak, long lastStreakDate) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CURRENT_STREAK, currentStreak);
        values.put(COLUMN_LONGEST_STREAK, longestStreak);
        values.put(COLUMN_LAST_STREAK_DATE, lastStreakDate);

        db.update(TABLE_TASKS, values,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(taskId)});

        logger.debug(TAG, "Updated streaks for task " + taskId);
    }

    /**
     * Convert Task object to ContentValues
     */
    private ContentValues taskToContentValues(Task task) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, task.getTitle());
        values.put(COLUMN_DESCRIPTION, task.getDescription());
        values.put(COLUMN_CATEGORY, task.getCategory());
        values.put(COLUMN_CREATED_AT, task.getCreatedAt());
        values.put(COLUMN_DUE_DATE, task.getDueDate());
        values.put(COLUMN_IS_COMPLETED, task.isCompleted() ? 1 : 0);
        values.put(COLUMN_PRIORITY, task.getPriority());
        values.put(COLUMN_RECURRENCE_TYPE, task.getRecurrenceType());
        values.put(COLUMN_RECURRENCE_AMOUNT, task.getRecurrenceAmount());
        values.put(COLUMN_RECURRENCE_UNIT, task.getRecurrenceUnit());
        values.put(COLUMN_LAST_COMPLETED_DATE, task.getLastCompletedDate());
        values.put(COLUMN_COMPLETIONS_THIS_PERIOD, task.getCompletionsThisPeriod());
        values.put(COLUMN_CURRENT_PERIOD_START, task.getCurrentPeriodStart());
        values.put(COLUMN_CURRENT_STREAK, task.getCurrentStreak());
        values.put(COLUMN_LONGEST_STREAK, task.getLongestStreak());
        values.put(COLUMN_LAST_STREAK_DATE, task.getLastStreakDate());

        return values;
    }

    /**
     * Convert Cursor to Task object
     */
    private Task cursorToTask(Cursor cursor) {
        Task task = new Task();

        task.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        task.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)));
        task.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION)));
        task.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
        task.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
        task.setDueDate(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DUE_DATE)));
        task.setCompleted(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_COMPLETED)) == 1);
        task.setPriority(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PRIORITY)));
        task.setRecurrenceType(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RECURRENCE_TYPE)));
        task.setRecurrenceAmount(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RECURRENCE_AMOUNT)));
        task.setRecurrenceUnit(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RECURRENCE_UNIT)));
        task.setLastCompletedDate(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_COMPLETED_DATE)));
        task.setCompletionsThisPeriod(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMPLETIONS_THIS_PERIOD)));
        task.setCurrentPeriodStart(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_PERIOD_START)));
        task.setCurrentStreak(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_STREAK)));
        task.setLongestStreak(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LONGEST_STREAK)));
        task.setLastStreakDate(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_STREAK_DATE)));

        return task;
    }

    /**
     * Close database connection
     */
    public void close() {
        dbHelper.close();
    }
}