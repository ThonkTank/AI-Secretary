package com.secretary.helloworld;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

import static com.secretary.helloworld.DatabaseConstants.*;

/**
 * Database helper for Task management.
 * Handles all database operations for tasks.
 */
public class TaskDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "TaskDatabaseHelper";
    // All database constants are now imported from DatabaseConstants class

    private AppLogger logger;
    private TaskStatistics statistics;

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
            COLUMN_CURRENT_PERIOD_START + " INTEGER DEFAULT 0, " +
            COLUMN_CURRENT_STREAK + " INTEGER DEFAULT 0, " +
            COLUMN_LONGEST_STREAK + " INTEGER DEFAULT 0, " +
            COLUMN_LAST_STREAK_DATE + " INTEGER DEFAULT 0" +
            ");";

    // SQL statement to create completions table
    private static final String CREATE_TABLE_COMPLETIONS =
            "CREATE TABLE " + TABLE_COMPLETIONS + " (" +
            COLUMN_COMPLETION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_TASK_ID + " INTEGER NOT NULL, " +
            COLUMN_COMPLETED_AT + " INTEGER NOT NULL, " +
            COLUMN_TIME_SPENT + " INTEGER DEFAULT 0, " +
            COLUMN_DIFFICULTY + " INTEGER DEFAULT 5, " +
            COLUMN_NOTES + " TEXT, " +
            "FOREIGN KEY(" + COLUMN_TASK_ID + ") REFERENCES " + TABLE_TASKS + "(" + COLUMN_ID + ")" +
            ");";

    public TaskDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.logger = AppLogger.getInstance(context);
        this.statistics = new TaskStatistics(this.getReadableDatabase());
        logger.info(TAG, "TaskDatabaseHelper initialized with TaskStatistics");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TASKS);
        db.execSQL(CREATE_TABLE_COMPLETIONS);
        logger.info(TAG, "Database created with tasks and completions tables");
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

        if (oldVersion < 4) {
            // Add completions table for tracking history
            try {
                db.execSQL(CREATE_TABLE_COMPLETIONS);
                logger.info(TAG, "Successfully created completions table");
            } catch (Exception e) {
                logger.error(TAG, "Error creating completions table", e);
            }
        }

        if (oldVersion < 5) {
            // Add streak tracking columns
            try {
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_CURRENT_STREAK + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_LONGEST_STREAK + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COLUMN_LAST_STREAK_DATE + " INTEGER DEFAULT 0");
                logger.info(TAG, "Successfully added streak tracking columns");
            } catch (Exception e) {
                logger.error(TAG, "Error adding streak columns", e);
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
        values.put(COLUMN_CURRENT_STREAK, task.getCurrentStreak());
        values.put(COLUMN_LONGEST_STREAK, task.getLongestStreak());
        values.put(COLUMN_LAST_STREAK_DATE, task.getLastStreakDate());

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

                // Streak fields - check if columns exist
                int streakIndex = cursor.getColumnIndex(COLUMN_CURRENT_STREAK);
                if (streakIndex >= 0) {
                    task.setCurrentStreak(cursor.getInt(streakIndex));
                    task.setLongestStreak(cursor.getInt(cursor.getColumnIndex(COLUMN_LONGEST_STREAK)));
                    task.setLastStreakDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_STREAK_DATE)));
                }

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

                // Streak fields - check if columns exist
                int streakIndex = cursor.getColumnIndex(COLUMN_CURRENT_STREAK);
                if (streakIndex >= 0) {
                    task.setCurrentStreak(cursor.getInt(streakIndex));
                    task.setLongestStreak(cursor.getInt(cursor.getColumnIndex(COLUMN_LONGEST_STREAK)));
                    task.setLastStreakDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_STREAK_DATE)));
                }

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
        values.put(COLUMN_CURRENT_STREAK, task.getCurrentStreak());
        values.put(COLUMN_LONGEST_STREAK, task.getLongestStreak());
        values.put(COLUMN_LAST_STREAK_DATE, task.getLastStreakDate());

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

    // Removed - merged with the newer version below that includes streak tracking

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

            // Streak fields - check if columns exist
            int streakIndex = cursor.getColumnIndex(COLUMN_CURRENT_STREAK);
            if (streakIndex >= 0) {
                task.setCurrentStreak(cursor.getInt(streakIndex));
                task.setLongestStreak(cursor.getInt(cursor.getColumnIndex(COLUMN_LONGEST_STREAK)));
                task.setLastStreakDate(cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_STREAK_DATE)));
            }
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

    /**
     * Get statistics for today
     */
    public int getTasksCompletedToday() {
        String todayStart = String.valueOf(getTodayStart());
        String query = "SELECT COUNT(*) FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_LAST_COMPLETED_DATE + " >= " + todayStart;

        SQLiteDatabase db = this.getReadableDatabase();
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
     * Get statistics for last 7 days
     */
    public int getTasksCompletedLast7Days() {
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        String query = "SELECT COUNT(*) FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_LAST_COMPLETED_DATE + " >= " + sevenDaysAgo;

        SQLiteDatabase db = this.getReadableDatabase();
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
     * Get overdue tasks count
     */
    public int getOverdueTasksCount() {
        long now = System.currentTimeMillis();
        String query = "SELECT COUNT(*) FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_DUE_DATE + " > 0" +
                      " AND " + COLUMN_DUE_DATE + " < " + now +
                      " AND " + COLUMN_IS_COMPLETED + " = 0";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return count;
    }

    private long getTodayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Save task completion with details
     */
    public long saveCompletion(long taskId, int timeSpentMinutes, int difficulty, String notes) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TASK_ID, taskId);
        values.put(COLUMN_COMPLETED_AT, System.currentTimeMillis());
        values.put(COLUMN_TIME_SPENT, timeSpentMinutes);
        values.put(COLUMN_DIFFICULTY, difficulty);
        values.put(COLUMN_NOTES, notes);

        long id = db.insert(TABLE_COMPLETIONS, null, values);
        db.close();

        logger.info(TAG, "Completion saved for task " + taskId + " (time: " + timeSpentMinutes +
                   " min, difficulty: " + difficulty + ")");
        return id;
    }

    /**
     * Get completion history for a task
     */
    public List<ContentValues> getTaskCompletionHistory(long taskId) {
        List<ContentValues> history = new ArrayList<>();

        String query = "SELECT * FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = " + taskId +
                      " ORDER BY " + COLUMN_COMPLETED_AT + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                ContentValues completion = new ContentValues();
                completion.put(COLUMN_COMPLETION_ID, cursor.getLong(cursor.getColumnIndex(COLUMN_COMPLETION_ID)));
                completion.put(COLUMN_COMPLETED_AT, cursor.getLong(cursor.getColumnIndex(COLUMN_COMPLETED_AT)));
                completion.put(COLUMN_TIME_SPENT, cursor.getInt(cursor.getColumnIndex(COLUMN_TIME_SPENT)));
                completion.put(COLUMN_DIFFICULTY, cursor.getInt(cursor.getColumnIndex(COLUMN_DIFFICULTY)));
                completion.put(COLUMN_NOTES, cursor.getString(cursor.getColumnIndex(COLUMN_NOTES)));
                history.add(completion);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        return history;
    }

    /**
     * Get average completion time for a task
     */
    public int getAverageCompletionTime(long taskId) {
        String query = "SELECT AVG(" + COLUMN_TIME_SPENT + ") FROM " + TABLE_COMPLETIONS +
                      " WHERE " + COLUMN_TASK_ID + " = " + taskId;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        int average = 0;
        if (cursor.moveToFirst()) {
            average = cursor.getInt(0);
        }

        cursor.close();
        db.close();

        return average;
    }

    /**
     * Mark a task as completed or not completed (with recurrence and streak support)
     */
    public void markTaskCompleted(long taskId, boolean isCompleted) {
        SQLiteDatabase db = this.getWritableDatabase();

        // First, get the task to check if it's recurring
        Task task = getTaskWithDb(db, taskId);
        if (task == null) {
            db.close();
            return;
        }

        if (isCompleted && task.isRecurring()) {
            // Handle recurring task completion
            handleRecurringTaskCompletion(db, task);

            // Update streak for recurring tasks too
            updateStreak(taskId);
        } else {
            // Non-recurring task or unchecking - just update status
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_COMPLETED, isCompleted ? 1 : 0);

            if (isCompleted) {
                values.put(COLUMN_LAST_COMPLETED_DATE, System.currentTimeMillis());

                // Update streak when marking as complete
                updateStreak(taskId);
            }

            db.update(TABLE_TASKS, values, COLUMN_ID + " = ?",
                     new String[]{String.valueOf(taskId)});
        }

        db.close();

        logger.info(TAG, "Task " + taskId + " marked as " +
                   (isCompleted ? "completed" : "active") +
                   (task.isRecurring() ? " (Recurring task handled)" : ""));
    }

    /**
     * Update streak when marking task as complete
     */
    private void updateStreak(long taskId) {
        Task task = getTask(taskId);
        if (task == null) return;

        long today = getTodayStart();
        long lastStreakDate = task.getLastStreakDate();

        // Check if this is a new day's completion
        if (lastStreakDate < today) {
            // Calculate if this continues the streak or starts a new one
            long yesterday = today - (24 * 60 * 60 * 1000);

            if (lastStreakDate >= yesterday && lastStreakDate < today) {
                // Continues the streak from yesterday
                task.setCurrentStreak(task.getCurrentStreak() + 1);
            } else {
                // Breaks the streak, start new one
                task.setCurrentStreak(1);
            }

            // Update longest streak if needed
            if (task.getCurrentStreak() > task.getLongestStreak()) {
                task.setLongestStreak(task.getCurrentStreak());
            }

            // Update last streak date to today
            task.setLastStreakDate(today);

            // Update in database
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_CURRENT_STREAK, task.getCurrentStreak());
            values.put(COLUMN_LONGEST_STREAK, task.getLongestStreak());
            values.put(COLUMN_LAST_STREAK_DATE, task.getLastStreakDate());

            db.update(TABLE_TASKS, values, COLUMN_ID + " = ?",
                     new String[]{String.valueOf(taskId)});
            db.close();

            logger.info(TAG, "Updated streak for task " + taskId +
                       " - Current: " + task.getCurrentStreak() +
                       ", Longest: " + task.getLongestStreak());
        }
    }

    // Removed duplicate - using the first getTask method instead

    /**
     * Get all unique categories used in tasks
     */
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();

        String query = "SELECT DISTINCT " + COLUMN_CATEGORY + " FROM " + TABLE_TASKS +
                      " WHERE " + COLUMN_CATEGORY + " IS NOT NULL" +
                      " ORDER BY " + COLUMN_CATEGORY + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                String category = cursor.getString(0);
                if (category != null && !category.isEmpty()) {
                    categories.add(category);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        logger.debug(TAG, "Retrieved " + categories.size() + " unique categories");
        return categories;
    }

    // ==================== Statistics Methods (Delegated to TaskStatistics) ====================

    /**
     * Get total task count
     * Delegates to TaskStatistics
     */
    public int getTaskCount() {
        return statistics.getTaskCount();
    }

    /**
     * Get count of tasks completed today
     * Delegates to TaskStatistics
     */
    public int getTasksCompletedToday() {
        return statistics.getTasksCompletedToday();
    }

    /**
     * Get count of tasks completed in last 7 days
     * Delegates to TaskStatistics
     */
    public int getTasksCompletedLast7Days() {
        return statistics.getTasksCompletedLast7Days();
    }

    /**
     * Get count of overdue tasks
     * Delegates to TaskStatistics
     */
    public int getOverdueTasksCount() {
        return statistics.getOverdueTasksCount();
    }

    /**
     * Get average completion time for a task
     * Delegates to TaskStatistics
     */
    public int getAverageCompletionTime(long taskId) {
        return statistics.getAverageCompletionTime(taskId);
    }

    /**
     * Get task completion history
     * Delegates to TaskStatistics
     */
    public List<ContentValues> getTaskCompletionHistory(long taskId) {
        return statistics.getTaskCompletionHistory(taskId);
    }
}