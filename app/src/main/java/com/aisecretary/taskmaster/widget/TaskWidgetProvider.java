package com.aisecretary.taskmaster.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.aisecretary.taskmaster.AddTaskActivity;
import com.aisecretary.taskmaster.MainActivity;
import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.utils.StatsManager;
import com.aisecretary.taskmaster.utils.StreakManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TaskWidgetProvider - Home Screen Widget Provider
 *
 * Phase 4.5.1: Large Widget (4x4) - Haupt-Widget
 * Displays next task, today's tasks, streak info, and quick actions.
 */
public class TaskWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_COMPLETE_TASK = "com.aisecretary.taskmaster.ACTION_COMPLETE_TASK";
    public static final String ACTION_REFRESH_WIDGET = "com.aisecretary.taskmaster.ACTION_REFRESH_WIDGET";
    public static final String EXTRA_TASK_ID = "task_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_COMPLETE_TASK.equals(action)) {
            // Handle task completion from widget
            long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
            if (taskId != -1) {
                TaskRepository repository = TaskRepository.getInstance(context);
                repository.completeTask(taskId);

                // Refresh all widgets
                updateAllWidgets(context);
            }
        } else if (ACTION_REFRESH_WIDGET.equals(action)) {
            // Manual refresh
            updateAllWidgets(context);
        }
    }

    /**
     * Update a single widget instance
     */
    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_large);

        TaskRepository repository = TaskRepository.getInstance(context);

        // Get data
        List<TaskEntity> todayTasks = repository.getTodayTasks();
        List<TaskEntity> streakTasks = repository.getTasksWithStreaks();
        int completedToday = repository.getCompletedTodayCount();

        // Update header - Streak
        if (!streakTasks.isEmpty()) {
            List<StatsManager.StreakSummary> topStreaks = StatsManager.getTopStreaks(streakTasks, 1);
            if (!topStreaks.isEmpty()) {
                StatsManager.StreakSummary topStreak = topStreaks.get(0);
                views.setTextViewText(R.id.widget_streak_text, topStreak.emoji + " " + topStreak.streak);
            }
        } else {
            views.setTextViewText(R.id.widget_streak_text, "🔥 0");
        }

        // Update header - Today count
        int totalToday = todayTasks.size() + completedToday;
        views.setTextViewText(R.id.widget_today_count, completedToday + "/" + totalToday);

        // Update next task section
        TaskEntity nextTask = repository.getNextTask();
        if (nextTask != null) {
            views.setTextViewText(R.id.widget_next_task_title, nextTask.title);

            // Priority stars
            String priorityStars = "";
            for (int i = 0; i < nextTask.priority; i++) {
                priorityStars += "⭐";
            }
            views.setTextViewText(R.id.widget_next_task_priority, priorityStars);

            // Due time
            String dueTime = formatDueTime(nextTask.dueAt);
            views.setTextViewText(R.id.widget_next_task_time, dueTime);

            // Streak badge
            if (nextTask.isRecurring && nextTask.currentStreak > 0) {
                views.setTextViewText(R.id.widget_next_task_streak, "🔥 " + nextTask.currentStreak);
                views.setViewVisibility(R.id.widget_next_task_streak, RemoteViews.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widget_next_task_streak, RemoteViews.GONE);
            }

            // Checkbox click to complete
            Intent completeIntent = new Intent(context, TaskWidgetProvider.class);
            completeIntent.setAction(ACTION_COMPLETE_TASK);
            completeIntent.putExtra(EXTRA_TASK_ID, nextTask.id);
            PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                    context, (int) nextTask.id, completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_next_task_checkbox, completePendingIntent);

        } else {
            views.setTextViewText(R.id.widget_next_task_title, "Keine Aufgaben 🎉");
            views.setTextViewText(R.id.widget_next_task_priority, "");
            views.setTextViewText(R.id.widget_next_task_time, "");
            views.setViewVisibility(R.id.widget_next_task_streak, RemoteViews.GONE);
        }

        // Setup today's tasks list
        Intent serviceIntent = new Intent(context, WidgetListService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setRemoteAdapter(R.id.widget_today_list, serviceIntent);

        // Add task button
        Intent addTaskIntent = new Intent(context, AddTaskActivity.class);
        PendingIntent addTaskPendingIntent = PendingIntent.getActivity(
                context, 0, addTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_add_task_button, addTaskPendingIntent);

        // Open app button
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_open_app_button, openAppPendingIntent);

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Update all widget instances
     */
    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, TaskWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        Intent updateIntent = new Intent(context, TaskWidgetProvider.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(updateIntent);
    }

    /**
     * Format due time in human-readable format
     */
    private String formatDueTime(long dueAt) {
        if (dueAt == 0) {
            return "";
        }

        long now = System.currentTimeMillis();
        long diff = dueAt - now;

        if (diff < 0) {
            // Overdue
            long overdueDays = Math.abs(diff) / (24 * 60 * 60 * 1000);
            if (overdueDays > 0) {
                return "⚠️ " + overdueDays + "d überfällig";
            } else {
                return "⚠️ Überfällig";
            }
        }

        // Due in the future
        long hours = diff / (60 * 60 * 1000);
        if (hours < 24) {
            if (hours == 0) {
                long minutes = diff / (60 * 1000);
                return "in " + minutes + " Min";
            }
            return "in " + hours + "h";
        }

        long days = hours / 24;
        if (days == 0) {
            return "Heute";
        } else if (days == 1) {
            return "Morgen";
        } else {
            return "in " + days + " Tagen";
        }
    }
}
