package com.aisecretary.taskmaster.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.aisecretary.taskmaster.MainActivity;
import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.utils.StatsManager;

import java.util.List;

/**
 * TaskWidgetProviderSmall - Small-sized widget (2x2)
 *
 * Shows only the next task in minimal format
 * Phase 4.5.2: Medium & Small Widgets
 */
public class TaskWidgetProviderSmall extends AppWidgetProvider {

    public static final String ACTION_COMPLETE_TASK = "com.aisecretary.taskmaster.ACTION_COMPLETE_TASK_SMALL";
    public static final String ACTION_REFRESH_WIDGET = "com.aisecretary.taskmaster.ACTION_REFRESH_WIDGET_SMALL";
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

        if (ACTION_COMPLETE_TASK.equals(intent.getAction())) {
            long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
            if (taskId != -1) {
                TaskRepository repository = TaskRepository.getInstance(context);
                repository.completeTask(taskId);
                updateAllWidgets(context);
            }
        } else if (ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            updateAllWidgets(context);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_small);

        TaskRepository repository = TaskRepository.getInstance(context);

        // Get data
        List<TaskEntity> streakTasks = repository.getTasksWithStreaks();
        List<StatsManager.StreakSummary> topStreaks = StatsManager.getTopStreaks(streakTasks, 1);
        List<TaskEntity> todayTasks = repository.getTodaysSortedTasks();
        TaskEntity nextTask = todayTasks.isEmpty() ? null : todayTasks.get(0);

        int completedToday = repository.getCompletedTodayCount();
        int totalToday = todayTasks.size() + completedToday;

        // Update streak
        if (!topStreaks.isEmpty()) {
            StatsManager.StreakSummary topStreak = topStreaks.get(0);
            views.setTextViewText(R.id.widget_streak_text, topStreak.emoji + " " + topStreak.streak);
        } else {
            views.setTextViewText(R.id.widget_streak_text, "🔥 0");
        }

        // Update today count
        views.setTextViewText(R.id.widget_today_count, completedToday + "/" + totalToday);

        // Update next task
        if (nextTask != null && !nextTask.completed) {
            views.setTextViewText(R.id.widget_next_task_title, nextTask.title);

            // Priority stars
            String stars = "";
            for (int i = 0; i < nextTask.priority; i++) {
                stars += "⭐";
            }
            views.setTextViewText(R.id.widget_next_task_priority, stars);

            // Due time
            if (nextTask.dueAt > 0) {
                views.setTextViewText(R.id.widget_next_task_time, formatDueTime(nextTask.dueAt));
            } else {
                views.setTextViewText(R.id.widget_next_task_time, "");
            }

            // Complete button click
            Intent completeIntent = new Intent(context, TaskWidgetProviderSmall.class);
            completeIntent.setAction(ACTION_COMPLETE_TASK);
            completeIntent.putExtra(EXTRA_TASK_ID, nextTask.id);
            PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                    context, (int) nextTask.id, completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_next_task_checkbox, completePendingIntent);

            // Container click opens app
            Intent openIntent = new Intent(context, MainActivity.class);
            PendingIntent openPendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_next_task_container, openPendingIntent);
        } else {
            views.setTextViewText(R.id.widget_next_task_title, "Keine Aufgaben 🎉");
            views.setTextViewText(R.id.widget_next_task_priority, "");
            views.setTextViewText(R.id.widget_next_task_time, "");

            // Container click opens app
            Intent openIntent = new Intent(context, MainActivity.class);
            PendingIntent openPendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_next_task_container, openPendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, TaskWidgetProviderSmall.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

        Intent intent = new Intent(context, TaskWidgetProviderSmall.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        context.sendBroadcast(intent);
    }

    private String formatDueTime(long dueAt) {
        long now = System.currentTimeMillis();
        long diff = dueAt - now;
        long days = diff / (24 * 60 * 60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        if (diff < 0) {
            long daysOverdue = Math.abs(days);
            return "⚠️ " + daysOverdue + "d";
        } else if (hours < 24) {
            return hours + "h";
        } else if (days == 0) {
            return "Heute";
        } else if (days == 1) {
            return "Morgen";
        } else {
            return days + "d";
        }
    }
}
