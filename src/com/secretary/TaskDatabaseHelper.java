package com.secretary.helloworld;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Database helper for Task management.
 * Handles all database operations for tasks.
 */
public class TaskDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "TaskDatabaseHelper";

    // Database configuration
    private static final String DATABASE_NAME = "taskmaster.db";
    private static final int DATABASE_VERSION = 1;

    // Table and column names
    private static final String TABLE_TASKS = "tasks";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_DESCRIPTION = "description";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_DUE_DATE = "due_date";
    private static final String COLUMN_IS_COMPLETED = "is_completed";
    private static final String COLUMN_PRIORITY = "priority";

    private AppLogger logger;

    // SQL statement to create tasks table
    private static final String CREATE_TABLE_TASKS =
            "CREATE TABLE " + TABLE_TASKS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_TITLE + " TEXT NOT NULL, " +
            COLUMN_DESCRIPTION + " TEXT, " +
            COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
            COLUMN_DUE_DATE + " INTEGER, " +
            COLUMN_IS_COMPLETED + " INTEGER DEFAULT 0, " +
            COLUMN_PRIORITY + " INTEGER DEFAULT 1" +
            ");";

    public TaskDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.logger = AppLogger.getInstance(context);
        logger.info(TAG, "TaskDatabaseHelper initialized");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TASKS);
        logger.info(TAG, "Database created with tasks table");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // For now, just drop and recreate
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TASKS);
        onCreate(db);
        logger.info(TAG, "Database upgraded from version " + oldVersion + " to " + newVersion);
    }

    // CRUD Operations

    /**
     * Insert a new task into the database
     */
    public long insertTask(Task task) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, task.getTitle());
        values.put(COLUMN_DESCRIPTION, task.getDescription());
        values.put(COLUMN_CREATED_AT, task.getCreatedAt());
        values.put(COLUMN_DUE_DATE, task.getDueDate());
        values.put(COLUMN_IS_COMPLETED, task.isCompleted() ? 1 : 0);
        values.put(COLUMN_PRIORITY, task.getPriority());

        long id = db.insert(TABLE_TASKS, null, values);
        db.close();

        logger.info(TAG, "Task inserted with ID: " + id);
        return id;
    }

    /**
     * Get all tasks from the database
     */
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + TABLE_TASKS +
                           " ORDER BY " + COLUMN_IS_COMPLETED + " ASC, " +
                           COLUMN_PRIORITY + " DESC, " +
                           COLUMN_CREATED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                task.setDescription(cursor.getString(cursor.getColumnIndex(COLUMN_DESCRIPTION)));
                task.setCreatedAt(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                task.setDueDate(cursor.getLong(cursor.getColumnIndex(COLUMN_DUE_DATE)));
                task.setCompleted(cursor.getInt(cursor.getColumnIndex(COLUMN_IS_COMPLETED)) == 1);
                task.setPriority(cursor.getInt(cursor.getColumnIndex(COLUMN_PRIORITY)));

                tasks.add(task);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        logger.debug(TAG, "Retrieved " + tasks.size() + " tasks from database");
        return tasks;
    }

    /**
     * Get active (not completed) tasks
     */
    public List<Task> getActiveTasks() {
        List<Task> tasks = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + TABLE_TASKS +
                           " WHERE " + COLUMN_IS_COMPLETED + " = 0" +
                           " ORDER BY " + COLUMN_PRIORITY + " DESC, " +
                           COLUMN_CREATED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Task task = new Task();
                task.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                task.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                task.setDescription(cursor.getString(cursor.getColumnIndex(COLUMN_DESCRIPTION)));
                task.setCreatedAt(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                task.setDueDate(cursor.getLong(cursor.getColumnIndex(COLUMN_DUE_DATE)));
                task.setCompleted(false);
                task.setPriority(cursor.getInt(cursor.getColumnIndex(COLUMN_PRIORITY)));

                tasks.add(task);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return tasks;
    }

    /**
     * Update an existing task
     */
    public int updateTask(Task task) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, task.getTitle());
        values.put(COLUMN_DESCRIPTION, task.getDescription());
        values.put(COLUMN_DUE_DATE, task.getDueDate());
        values.put(COLUMN_IS_COMPLETED, task.isCompleted() ? 1 : 0);
        values.put(COLUMN_PRIORITY, task.getPriority());

        int rowsAffected = db.update(TABLE_TASKS, values,
                                     COLUMN_ID + " = ?",
                                     new String[]{String.valueOf(task.getId())});
        db.close();

        logger.info(TAG, "Task updated, rows affected: " + rowsAffected);
        return rowsAffected;
    }

    /**
     * Delete a task
     */
    public int deleteTask(long taskId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_TASKS,
                                    COLUMN_ID + " = ?",
                                    new String[]{String.valueOf(taskId)});
        db.close();

        logger.info(TAG, "Task deleted with ID: " + taskId);
        return rowsDeleted;
    }

    /**
     * Mark a task as completed
     */
    public void markTaskCompleted(long taskId, boolean completed) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_COMPLETED, completed ? 1 : 0);

        db.update(TABLE_TASKS, values,
                 COLUMN_ID + " = ?",
                 new String[]{String.valueOf(taskId)});
        db.close();

        logger.info(TAG, "Task " + taskId + " marked as " + (completed ? "completed" : "active"));
    }

    /**
     * Get task count
     */
    public int getTaskCount() {
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_TASKS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return count;
    }
}