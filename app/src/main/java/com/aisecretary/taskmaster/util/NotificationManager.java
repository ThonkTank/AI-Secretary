package com.aisecretary.taskmaster.util;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.aisecretary.taskmaster.MainActivity;
import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NotificationManager - Manages all app notifications
 *
 * Handles:
 * - Task reminders for due tasks
 * - Daily summary notifications
 * - Streak danger warnings
 * - Notification channels setup
 *
 * Phase 8.1
 */
public class NotificationManager {

    // Notification Channel IDs
    private static final String CHANNEL_ID_REMINDERS = "task_reminders";
    private static final String CHANNEL_ID_DAILY = "daily_summary";
    private static final String CHANNEL_ID_STREAKS = "streak_warnings";

    // Notification IDs
    private static final int NOTIFICATION_ID_DAILY_SUMMARY = 1000;
    private static final int NOTIFICATION_ID_STREAK_WARNING = 2000;
    private static final int NOTIFICATION_ID_TASK_REMINDER_BASE = 3000;

    /**
     * Create notification channels (required for Android 8.0+)
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager manager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Channel 1: Task Reminders
            NotificationChannel remindersChannel = new NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Task-Erinnerungen",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            remindersChannel.setDescription("Erinnerungen für fällige Aufgaben");
            remindersChannel.enableVibration(true);
            remindersChannel.setShowBadge(true);
            manager.createNotificationChannel(remindersChannel);

            // Channel 2: Daily Summary
            NotificationChannel dailyChannel = new NotificationChannel(
                CHANNEL_ID_DAILY,
                "Tägliche Zusammenfassung",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            dailyChannel.setDescription("Tägliche Übersicht deiner Aufgaben");
            dailyChannel.setShowBadge(true);
            manager.createNotificationChannel(dailyChannel);

            // Channel 3: Streak Warnings
            NotificationChannel streakChannel = new NotificationChannel(
                CHANNEL_ID_STREAKS,
                "Streak-Warnungen",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            streakChannel.setDescription("Warnungen wenn Streaks in Gefahr sind");
            streakChannel.enableVibration(true);
            streakChannel.setShowBadge(true);
            manager.createNotificationChannel(streakChannel);
        }
    }

    /**
     * Show notification for a due task
     */
    public static void showTaskReminder(Context context, TaskEntity task) {
        // Create intent to open app
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        String title = task.getPriorityStars() + " " + task.title;
        String message;

        if (task.isOverdue()) {
            long daysOverdue = task.getOverdueDuration() / 86400000;
            message = "⚠️ Überfällig seit " + daysOverdue + " Tag" + (daysOverdue > 1 ? "en" : "");
        } else if (task.isDueToday()) {
            message = "📅 Heute fällig";
        } else {
            message = "📅 Bald fällig";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        // Add streak info if recurring
        if (task.isRecurring && task.currentStreak > 0) {
            builder.setSubText("🔥 Streak: " + task.currentStreak);
        }

        // Show notification
        android.app.NotificationManager manager =
            (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID_TASK_REMINDER_BASE + task.id, builder.build());
    }

    /**
     * Show daily summary notification
     */
    public static void showDailySummary(Context context, int todayCount, int completedToday,
                                       int overdueCount, int streaksAtRisk) {
        // Create intent to open app
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification content
        String title = "📊 Deine Tagesübersicht";
        String message;

        if (todayCount == 0) {
            message = "🎉 Keine Aufgaben für heute!";
        } else {
            int remaining = todayCount - completedToday;
            message = remaining + " Aufgabe" + (remaining != 1 ? "n" : "") + " übrig";

            if (completedToday > 0) {
                message += " • " + completedToday + " erledigt ✓";
            }
        }

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_DAILY)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        // Add details as big text
        StringBuilder details = new StringBuilder();
        details.append(message);

        if (overdueCount > 0) {
            details.append("\n⚠️ ").append(overdueCount)
                   .append(" überfällige Aufgabe").append(overdueCount > 1 ? "n" : "");
        }

        if (streaksAtRisk > 0) {
            details.append("\n🔥 ").append(streaksAtRisk)
                   .append(" Streak").append(streaksAtRisk > 1 ? "s" : "")
                   .append(" in Gefahr!");
        }

        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(details.toString()));

        // Show notification
        android.app.NotificationManager manager =
            (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID_DAILY_SUMMARY, builder.build());
    }

    /**
     * Show streak danger warning
     */
    public static void showStreakWarning(Context context, List<TaskEntity> tasksAtRisk) {
        if (tasksAtRisk.isEmpty()) {
            return;
        }

        // Create intent to open app
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        String title = "🔥 Streak-Warnung!";
        String message;

        if (tasksAtRisk.size() == 1) {
            TaskEntity task = tasksAtRisk.get(0);
            message = task.title + " • Streak: " + task.currentStreak;
        } else {
            message = tasksAtRisk.size() + " Streaks in Gefahr!";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_STREAKS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        // Add details as big text
        if (tasksAtRisk.size() > 1) {
            StringBuilder details = new StringBuilder();
            for (int i = 0; i < Math.min(5, tasksAtRisk.size()); i++) {
                TaskEntity task = tasksAtRisk.get(i);
                details.append(task.getPriorityStars()).append(" ")
                       .append(task.title)
                       .append(" • 🔥 ").append(task.currentStreak);

                if (i < tasksAtRisk.size() - 1) {
                    details.append("\n");
                }
            }

            if (tasksAtRisk.size() > 5) {
                details.append("\n... und ").append(tasksAtRisk.size() - 5).append(" mehr");
            }

            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(details.toString()));
        }

        // Show notification
        android.app.NotificationManager manager =
            (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID_STREAK_WARNING, builder.build());
    }

    /**
     * Cancel all notifications
     */
    public static void cancelAllNotifications(Context context) {
        android.app.NotificationManager manager =
            (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancelAll();
    }

    /**
     * Cancel task reminder notification
     */
    public static void cancelTaskReminder(Context context, int taskId) {
        android.app.NotificationManager manager =
            (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID_TASK_REMINDER_BASE + taskId);
    }

    /**
     * Format time for notification
     */
    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
