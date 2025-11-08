package com.aisecretary.taskmaster.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.database.TaskRepository;
import com.aisecretary.taskmaster.util.NotificationManager;
import com.aisecretary.taskmaster.util.StatsManager;

import java.util.Calendar;
import java.util.List;

/**
 * NotificationService - Background service for scheduled notifications
 *
 * Handles:
 * - Daily summary at 8:00 AM
 * - Task reminders for due/overdue tasks
 * - Streak warnings for tasks at risk
 *
 * Uses AlarmManager for scheduling
 * Phase 8.1
 */
public class NotificationService extends Service {

    private static final String ACTION_CHECK_TASKS = "com.aisecretary.taskmaster.ACTION_CHECK_TASKS";
    private static final String ACTION_DAILY_SUMMARY = "com.aisecretary.taskmaster.ACTION_DAILY_SUMMARY";
    private static final String ACTION_STREAK_WARNING = "com.aisecretary.taskmaster.ACTION_STREAK_WARNING";

    /**
     * Start the notification service
     */
    public static void startService(Context context) {
        // Create notification channels
        NotificationManager.createNotificationChannels(context);

        // Schedule daily summary (8:00 AM)
        scheduleDailySummary(context);

        // Schedule task checks (every 2 hours)
        scheduleTaskChecks(context);

        // Schedule streak warnings (9:00 AM)
        scheduleStreakWarnings(context);
    }

    /**
     * Schedule daily summary notification (8:00 AM)
     */
    private static void scheduleDailySummary(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(ACTION_DAILY_SUMMARY);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set to 8:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If already past 8 AM today, schedule for tomorrow
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule repeating alarm
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            );
        } catch (Exception e) {
            // Fallback if exact alarm permission not granted
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            );
        }
    }

    /**
     * Schedule task reminder checks (every 2 hours)
     */
    private static void scheduleTaskChecks(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(ACTION_CHECK_TASKS);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule every 2 hours
        long interval = AlarmManager.INTERVAL_HOUR * 2;

        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            );
        } catch (Exception e) {
            // Fallback
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pendingIntent
            );
        }
    }

    /**
     * Schedule streak warning checks (9:00 AM)
     */
    private static void scheduleStreakWarnings(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(ACTION_STREAK_WARNING);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            1003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set to 9:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If already past 9 AM today, schedule for tomorrow
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule repeating alarm
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            );
        } catch (Exception e) {
            // Fallback
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            );
        }
    }

    /**
     * Cancel all scheduled notifications
     */
    public static void cancelScheduledNotifications(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Cancel daily summary
        Intent dailyIntent = new Intent(context, NotificationReceiver.class);
        dailyIntent.setAction(ACTION_DAILY_SUMMARY);
        PendingIntent dailyPendingIntent = PendingIntent.getBroadcast(
            context, 1001, dailyIntent, PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(dailyPendingIntent);

        // Cancel task checks
        Intent checkIntent = new Intent(context, NotificationReceiver.class);
        checkIntent.setAction(ACTION_CHECK_TASKS);
        PendingIntent checkPendingIntent = PendingIntent.getBroadcast(
            context, 1002, checkIntent, PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(checkPendingIntent);

        // Cancel streak warnings
        Intent streakIntent = new Intent(context, NotificationReceiver.class);
        streakIntent.setAction(ACTION_STREAK_WARNING);
        PendingIntent streakPendingIntent = PendingIntent.getBroadcast(
            context, 1003, streakIntent, PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(streakPendingIntent);

        // Cancel all displayed notifications
        NotificationManager.cancelAllNotifications(context);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * BroadcastReceiver for handling scheduled notifications
     */
    public static class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_DAILY_SUMMARY.equals(action)) {
                handleDailySummary(context);
            } else if (ACTION_CHECK_TASKS.equals(action)) {
                handleTaskChecks(context);
            } else if (ACTION_STREAK_WARNING.equals(action)) {
                handleStreakWarnings(context);
            }
        }

        /**
         * Handle daily summary notification
         */
        private void handleDailySummary(Context context) {
            TaskRepository repository = TaskRepository.getInstance(context);

            List<TaskEntity> todayTasks = repository.getTodaysSortedTasks();
            List<TaskEntity> overdueTasks = repository.getOverdueTasks();
            List<TaskEntity> streaksAtRisk = repository.getTasksWithStreaksAtRisk();

            int todayCount = todayTasks.size();
            int completedToday = 0;

            for (TaskEntity task : todayTasks) {
                if (task.completed) {
                    completedToday++;
                }
            }

            NotificationManager.showDailySummary(
                context,
                todayCount,
                completedToday,
                overdueTasks.size(),
                streaksAtRisk.size()
            );
        }

        /**
         * Handle task reminder checks
         */
        private void handleTaskChecks(Context context) {
            TaskRepository repository = TaskRepository.getInstance(context);

            // Get tasks that are due today or overdue
            List<TaskEntity> todayTasks = repository.getTodayTasks();
            List<TaskEntity> overdueTasks = repository.getOverdueTasks();

            // Send reminders for top 3 overdue tasks
            int overdueCount = 0;
            for (TaskEntity task : overdueTasks) {
                if (!task.completed && overdueCount < 3) {
                    NotificationManager.showTaskReminder(context, task);
                    overdueCount++;
                }
            }

            // If no overdue tasks, remind about next task
            if (overdueCount == 0 && !todayTasks.isEmpty()) {
                TaskEntity nextTask = repository.getNextTask();
                if (nextTask != null && !nextTask.completed) {
                    NotificationManager.showTaskReminder(context, nextTask);
                }
            }
        }

        /**
         * Handle streak warning notifications
         */
        private void handleStreakWarnings(Context context) {
            TaskRepository repository = TaskRepository.getInstance(context);

            List<TaskEntity> streaksAtRisk = repository.getTasksWithStreaksAtRisk();

            if (!streaksAtRisk.isEmpty()) {
                NotificationManager.showStreakWarning(context, streaksAtRisk);
            }
        }
    }
}
