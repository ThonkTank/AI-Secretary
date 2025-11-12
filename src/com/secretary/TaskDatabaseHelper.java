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
    private static final int DATABASE_VERSION = 3; // Incremented for category support

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

    // Recurrence columns
    private static final String COLUMN_RECURRENCE_TYPE = "recurrence_type";
    private static final String COLUMN_RECURRENCE_AMOUNT = "recurrence_amount";
    private static final String COLUMN_RECURRENCE_UNIT = "recurrence_unit";
    private static final String COLUMN_LAST_COMPLETED_DATE = "last_completed_date";
    private static final String COLUMN_COMPLETIONS_THIS_PERIOD = "completions_this_period";
    private static final String COLUMN_CURRENT_PERIOD_START = "current_period_start";

    private AppLogger logger;

    // SQL statement to create tasks table
    private static final String CREATE_TABLE_TASKS =
            "CREATE TABLE " + TABLE_TASKS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_TITLE + " TEXT NOT NULL, " +
            COLUMN_DESCRIPTION + " TEXT, " +
            COLUMN_CATEGORY + " TEXT DEFAULT 'General', " +
            COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
            COLUMN_DUE_DATE + " INTEGER, " +
            COLUMN_IS_COMPLETED + " INTEGER DEFAULT 0, " +
            COLUMN_PRIORITY + " INTEGER DEFAULT 1, " +
            COLUMN_RECURRENCE_TYPE + " INTEGER DEFAULT 0, " +
            COLUMN_RECURRENCE_AMOUNT + " INTEGER DEFAULT 0, " +
            COLUMN_RECURRENCE_UNIT + " INTEGER DEFAULT 0, " +
            COLUMN_LAST_COMPLETED_DATE + " INTEGER DEFAULT 0, " +
            COLUMN_COMPLETIONS_THIS_PERIOD + " INTEGER DEFAULT 0, " +
            COLUMN_CURRENT_PERIOD_START + " INTEGER DEFAULT 0" +
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
        logger.info(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 2) {
            // Add recurrence columns for existing database
            try {
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_RECURRENCE_TYPE + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_RECURRENCE_AMOUNT + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_RECURRENCE_UNIT + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_LAST_COMPLETED_DATE + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_COMPLETIONS_THIS_PERIOD + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_CURRENT_PERIOD_START + " INTEGER DEFAULT 0");
                logger.info(TAG, "Successfully added recurrence columns");
            } catch (Exception e) {
                logger.error(TAG, "Error upgrading database, recreating", e);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_TASKS);
                onCreate(db);
            }
        }

        if (oldVersion < 3) {
            // Add category column for existing database
            try {
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_CATEGORY + " TEXT DEFAULT 'General'");
                logger.info(TAG, "Successfully added category column");
            } catch (Exception e) {
                logger.error(TAG, "Error adding category column", e);
            }
        }
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
        values.put(COLUMN_CATEGORY, task.getCategory());
        values.put(COLUMN_CREATED_AT, task.getCreatedAt());
        values.put(COLUMN_DUE_DATE, task.getDueDate());
        values.put(COLUMN_IS_COMPLETED, task.isCompleted() ? 1 : 0);
        values.put(COLUMN_PRIORITY, task.getPriority());

        // Recurrence fields
        values.put(COLUMN_RECURRENCE_TYPE, task.getRecurrenceType());
        values.put(COLUMN_RECURRENCE_AMOUNT, task.getRecurrenceAmount());
        values.put(COLUMN_RECURRENCE_UNIT, task.getRecurrenceUnit());
        values.put(COLUMN_LAST_COMPLETED_DATE, task.getLastCompletedDate());
        values.put(COLUMN_COMPLETIONS_THIS_PERIOD, task.getCompletionsThisPeriod());
        values.put(COLUMN_CURRENT_PERIOD_START, task.getCurrentPeriodStart());

        long id = db.insert(TABLE_TASKS, null, values);
        db.close();

        logger.info(TAG, "Task inserted with ID: " + id);
        return id;
    }

    /**
     * Get all tasks from the database
     */
    public List<Task> getAllTasks() {
        // First, check and reset any due recurring tasks
        resetDueRecurringTasks();

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
                task.setCategory(cursor.getString(cursor.getColumnIndex(COLUMN_CATEGORY)));
                task.setCreatedAt(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                task.setDueDate(cursor.getLong(cursor.getColumnIndex(COLUMN_DUE_DATE)));
                task.setCompleted(cursor.getInt(cursor.getColumnIndex(COLUMN_IS_COMPLETED)) == 1);
                task.setPriority(cursor.getInt(cursor.getColumnIndex(COLUMN_PRIORITY)));

                // Recurrence fields
                task.setRecurrenceType(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_TYPE)));
                task.setRecurrenceAmount(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_AMOUNT)));
                task.setRecurrenceUnit(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_UNIT)));
                task.setLastCompletedDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_COMPLETED_DATE)));
                task.setCompletionsThisPeriod(cursor.getInt(cursor.getColumnIndex(COLUMN_COMPLETIONS_THIS_PERIOD)));
                task.setCurrentPeriodStart(cursor.getLong(cursor.getColumnIndex(COLUMN_CURRENT_PERIOD_START)));

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
                task.setCategory(cursor.getString(cursor.getColumnIndex(COLUMN_CATEGORY)));
                task.setCreatedAt(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
                task.setDueDate(cursor.getLong(cursor.getColumnIndex(COLUMN_DUE_DATE)));
                task.setCompleted(false);
                task.setPriority(cursor.getInt(cursor.getColumnIndex(COLUMN_PRIORITY)));

                // Recurrence fields
                task.setRecurrenceType(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_TYPE)));
                task.setRecurrenceAmount(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_AMOUNT)));
                task.setRecurrenceUnit(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_UNIT)));
                task.setLastCompletedDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_COMPLETED_DATE)));
                task.setCompletionsThisPeriod(cursor.getInt(cursor.getColumnIndex(COLUMN_COMPLETIONS_THIS_PERIOD)));
                task.setCurrentPeriodStart(cursor.getLong(cursor.getColumnIndex(COLUMN_CURRENT_PERIOD_START)));

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
        values.put(COLUMN_CATEGORY, task.getCategory());
        values.put(COLUMN_DUE_DATE, task.getDueDate());
        values.put(COLUMN_IS_COMPLETED, task.isCompleted() ? 1 : 0);
        values.put(COLUMN_PRIORITY, task.getPriority());

        // Recurrence fields
        values.put(COLUMN_RECURRENCE_TYPE, task.getRecurrenceType());
        values.put(COLUMN_RECURRENCE_AMOUNT, task.getRecurrenceAmount());
        values.put(COLUMN_RECURRENCE_UNIT, task.getRecurrenceUnit());
        values.put(COLUMN_LAST_COMPLETED_DATE, task.getLastCompletedDate());
        values.put(COLUMN_COMPLETIONS_THIS_PERIOD, task.getCompletionsThisPeriod());
        values.put(COLUMN_CURRENT_PERIOD_START, task.getCurrentPeriodStart());

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
     * Mark a task as completed and handle recurrence
     */
    public void markTaskCompleted(long taskId, boolean completed) {
        SQLiteDatabase db = this.getWritableDatabase();

        // First, get the task to check if it's recurring
        Task task = getTaskWithDb(db, taskId);
        if (task == null) {
            db.close();
            return;
        }

        if (completed && task.isRecurring()) {
            handleRecurringTaskCompletion(db, task);
        } else {
            // Non-recurring task or unchecking - just update status
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_COMPLETED, completed ? 1 : 0);

            db.update(TABLE_TASKS, values,
                     COLUMN_ID + " = ?",
                     new String[]{String.valueOf(taskId)});
        }

        db.close();
        logger.info(TAG, "Task " + taskId + " marked as " + (completed ? "completed" : "active") +
                   (task.isRecurring() ? " (Recurring task handled)" : ""));
    }

    /**
     * Handle completion of a recurring task
     */
    private void handleRecurringTaskCompletion(SQLiteDatabase db, Task task) {
        long now = System.currentTimeMillis();

        if (task.getRecurrenceType() == Task.RECURRENCE_INTERVAL) {
            // INTERVAL type: Mark as completed and set next due date
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_COMPLETED, 1); // Keep as completed
            values.put(COLUMN_LAST_COMPLETED_DATE, now);

            // Calculate next due date based on current time
            long nextDueDate = calculateNextDueDate(now,
                                                   task.getRecurrenceAmount(),
                                                   task.getRecurrenceUnit());
            values.put(COLUMN_DUE_DATE, nextDueDate);

            db.update(TABLE_TASKS, values,
                     COLUMN_ID + " = ?",
                     new String[]{String.valueOf(task.getId())});

            logger.info(TAG, "Interval recurring task completed, next due: " +
                       new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(nextDueDate)) +
                       " - " + task.getTitle());

        } else if (task.getRecurrenceType() == Task.RECURRENCE_FREQUENCY) {
            // FREQUENCY type: Track completions within period
            int completions = task.getCompletionsThisPeriod();
            long periodStart = task.getCurrentPeriodStart();

            // Check if we're still in the same period
            if (!isInCurrentPeriod(periodStart, task.getRecurrenceUnit(), now)) {
                // New period - reset counters
                completions = 0;
                periodStart = getPeriodStart(now, task.getRecurrenceUnit());
            }

            completions++; // Increment completion count

            ContentValues values = new ContentValues();

            if (completions >= task.getRecurrenceAmount()) {
                // Target reached for this period - mark as completed
                values.put(COLUMN_IS_COMPLETED, 1);
                values.put(COLUMN_COMPLETIONS_THIS_PERIOD, completions);
                logger.info(TAG, "Frequency task completed for period: " + task.getTitle() +
                          " (" + completions + "/" + task.getRecurrenceAmount() + ")");
            } else {
                // Not yet reached target - keep uncompleted
                values.put(COLUMN_IS_COMPLETED, 0);
                values.put(COLUMN_COMPLETIONS_THIS_PERIOD, completions);
                logger.info(TAG, "Frequency task progress: " + task.getTitle() +
                          " (" + completions + "/" + task.getRecurrenceAmount() + ")");
            }

            values.put(COLUMN_CURRENT_PERIOD_START, periodStart);
            values.put(COLUMN_LAST_COMPLETED_DATE, now);

            db.update(TABLE_TASKS, values,
                     COLUMN_ID + " = ?",
                     new String[]{String.valueOf(task.getId())});
        }
    }

    /**
     * Get a single task by ID using provided database connection
     */
    private Task getTaskWithDb(SQLiteDatabase db, long taskId) {
        Cursor cursor = db.query(TABLE_TASKS,
                                null,
                                COLUMN_ID + " = ?",
                                new String[]{String.valueOf(taskId)},
                                null, null, null);

        Task task = null;
        if (cursor.moveToFirst()) {
            task = new Task();
            task.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
            task.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
            task.setDescription(cursor.getString(cursor.getColumnIndex(COLUMN_DESCRIPTION)));
            task.setCategory(cursor.getString(cursor.getColumnIndex(COLUMN_CATEGORY)));
            task.setCreatedAt(cursor.getLong(cursor.getColumnIndex(COLUMN_CREATED_AT)));
            task.setDueDate(cursor.getLong(cursor.getColumnIndex(COLUMN_DUE_DATE)));
            task.setCompleted(cursor.getInt(cursor.getColumnIndex(COLUMN_IS_COMPLETED)) == 1);
            task.setPriority(cursor.getInt(cursor.getColumnIndex(COLUMN_PRIORITY)));

            // Recurrence fields
            task.setRecurrenceType(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_TYPE)));
            task.setRecurrenceAmount(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_AMOUNT)));
            task.setRecurrenceUnit(cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_UNIT)));
            task.setLastCompletedDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_COMPLETED_DATE)));
            task.setCompletionsThisPeriod(cursor.getInt(cursor.getColumnIndex(COLUMN_COMPLETIONS_THIS_PERIOD)));
            task.setCurrentPeriodStart(cursor.getLong(cursor.getColumnIndex(COLUMN_CURRENT_PERIOD_START)));
        }

        cursor.close();
        // Do NOT close the database connection here - caller manages it

        return task;
    }

    /**
     * Get a single task by ID (opens own database connection)
     */
    private Task getTask(long taskId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Task task = getTaskWithDb(db, taskId);
        db.close();
        return task;
    }

    /**
     * Calculate the next due date based on recurrence settings
     */
    private long calculateNextDueDate(long currentDueDate, int amount, int unit) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(currentDueDate);

        switch (unit) {
            case Task.UNIT_DAY:
                cal.add(java.util.Calendar.DAY_OF_MONTH, amount);
                break;
            case Task.UNIT_WEEK:
                cal.add(java.util.Calendar.WEEK_OF_YEAR, amount);
                break;
            case Task.UNIT_MONTH:
                cal.add(java.util.Calendar.MONTH, amount);
                break;
            case Task.UNIT_YEAR:
                cal.add(java.util.Calendar.YEAR, amount);
                break;
        }

        return cal.getTimeInMillis();
    }

    /**
     * Check if a timestamp is in the current period
     */
    private boolean isInCurrentPeriod(long periodStart, int unit, long now) {
        if (periodStart == 0) return false;

        java.util.Calendar periodCal = java.util.Calendar.getInstance();
        periodCal.setTimeInMillis(periodStart);

        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        nowCal.setTimeInMillis(now);

        switch (unit) {
            case Task.UNIT_DAY:
                return periodCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                       periodCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR);

            case Task.UNIT_WEEK:
                return periodCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                       periodCal.get(java.util.Calendar.WEEK_OF_YEAR) == nowCal.get(java.util.Calendar.WEEK_OF_YEAR);

            case Task.UNIT_MONTH:
                return periodCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
                       periodCal.get(java.util.Calendar.MONTH) == nowCal.get(java.util.Calendar.MONTH);

            case Task.UNIT_YEAR:
                return periodCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR);

            default:
                return false;
        }
    }

    /**
     * Get the start of the current period
     */
    private long getPeriodStart(long timestamp, int unit) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);

        // Reset to start of period
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        switch (unit) {
            case Task.UNIT_DAY:
                // Already at start of day
                break;

            case Task.UNIT_WEEK:
                cal.set(java.util.Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                break;

            case Task.UNIT_MONTH:
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                break;

            case Task.UNIT_YEAR:
                cal.set(java.util.Calendar.DAY_OF_YEAR, 1);
                break;
        }

        return cal.getTimeInMillis();
    }

    /**
     * Reset recurring tasks that are due to reappear
     */
    private void resetDueRecurringTasks() {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = this.getWritableDatabase();

        // Query for completed interval recurring tasks
        String query = "SELECT * FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_IS_COMPLETED + " = 1" +
                      " AND " + COLUMN_RECURRENCE_TYPE + " = " + Task.RECURRENCE_INTERVAL +
                      " AND " + COLUMN_DUE_DATE + " > 0" +
                      " AND " + COLUMN_DUE_DATE + " <= " + now;

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                long taskId = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
                String taskTitle = cursor.getString(cursor.getColumnIndex(COLUMN_TITLE));

                // Reset task to uncompleted
                ContentValues values = new ContentValues();
                values.put(COLUMN_IS_COMPLETED, 0);
                values.put(COLUMN_DUE_DATE, 0); // Clear due date until next completion

                db.update(TABLE_TASKS, values,
                         COLUMN_ID + " = ?",
                         new String[]{String.valueOf(taskId)});

                logger.info(TAG, "Recurring task reset (due): " + taskTitle);
            } while (cursor.moveToNext());
        }
        cursor.close();

        // Also check frequency tasks for period resets
        checkFrequencyTaskPeriods(db, now);

        db.close();
    }

    /**
     * Check and reset frequency tasks if their period has expired
     */
    private void checkFrequencyTaskPeriods(SQLiteDatabase db, long now) {
        String query = "SELECT * FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_RECURRENCE_TYPE + " = " + Task.RECURRENCE_FREQUENCY;

        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                long taskId = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
                String taskTitle = cursor.getString(cursor.getColumnIndex(COLUMN_TITLE));
                int unit = cursor.getInt(cursor.getColumnIndex(COLUMN_RECURRENCE_UNIT));
                long periodStart = cursor.getLong(cursor.getColumnIndex(COLUMN_CURRENT_PERIOD_START));
                int completions = cursor.getInt(cursor.getColumnIndex(COLUMN_COMPLETIONS_THIS_PERIOD));

                // Check if we're in a new period
                if (periodStart > 0 && !isInCurrentPeriod(periodStart, unit, now)) {
                    // New period - reset counters
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_COMPLETIONS_THIS_PERIOD, 0);
                    values.put(COLUMN_CURRENT_PERIOD_START, getPeriodStart(now, unit));
                    values.put(COLUMN_IS_COMPLETED, 0); // Reset to uncompleted for new period

                    db.update(TABLE_TASKS, values,
                             COLUMN_ID + " = ?",
                             new String[]{String.valueOf(taskId)});

                    logger.info(TAG, "Frequency task period reset: " + taskTitle +
                               " (had " + completions + " completions last period)");
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
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