package com.aisecretary.taskmaster.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.aisecretary.taskmaster.repository.TaskRepository;

import java.util.Calendar;

/**
 * RecurringTaskService - Background service for automatic recurring task reset
 *
 * This service runs periodically to check if any recurring tasks should be reset
 * (marked as incomplete again based on their recurrence pattern).
 * Phase 2.3: Wiederkehrende Tasks - Erweitert
 */
public class RecurringTaskService extends Service {

    private static final String TAG = "RecurringTaskService";
    private static final String ACTION_CHECK_TASKS = "com.aisecretary.taskmaster.ACTION_CHECK_TASKS";

    // Check interval: Every 1 hour
    private static final long CHECK_INTERVAL_MS = 60 * 60 * 1000;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "RecurringTaskService started");

        if (intent != null && ACTION_CHECK_TASKS.equals(intent.getAction())) {
            checkAndResetTasks();
        }

        // Schedule next check
        scheduleNextCheck(this);

        // Don't restart if killed
        return START_NOT_STICKY;
    }

    /**
     * Check and reset recurring tasks
     */
    private void checkAndResetTasks() {
        Log.d(TAG, "Checking recurring tasks...");

        TaskRepository repository = TaskRepository.getInstance(this);
        repository.checkAndResetRecurringTasks();

        Log.d(TAG, "Recurring tasks check completed");
    }

    /**
     * Schedule the next recurring task check
     */
    public static void scheduleNextCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RecurringTaskService.class);
        intent.setAction(ACTION_CHECK_TASKS);

        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Cancel any existing alarm
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        // Calculate next check time (next hour on the hour)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long nextCheckTime = calendar.getTimeInMillis();

        // Schedule next alarm
        if (alarmManager != null) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextCheckTime,
                        pendingIntent
                );
                Log.d(TAG, "Next recurring task check scheduled for: " + calendar.getTime());
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied for exact alarm", e);
                // Fallback to inexact alarm
                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        nextCheckTime,
                        pendingIntent
                );
            }
        }
    }

    /**
     * Cancel scheduled recurring task checks
     */
    public static void cancelScheduledChecks(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, RecurringTaskService.class);
        intent.setAction(ACTION_CHECK_TASKS);

        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Recurring task checks cancelled");
        }
    }

    /**
     * Start the recurring task service and schedule checks
     */
    public static void startService(Context context) {
        Intent intent = new Intent(context, RecurringTaskService.class);
        intent.setAction(ACTION_CHECK_TASKS);
        context.startService(intent);

        Log.d(TAG, "Recurring task service started");
    }
}
