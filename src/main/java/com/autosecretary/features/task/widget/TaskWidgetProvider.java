package com.autosecretary.features.task.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.MainActivity;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.TaskCompletionService;
import com.autosecretary.features.task.domain.TaskLifecycleManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TaskWidget";

    static final String ACTION_TOGGLE = "com.autosecretary.widget.TOGGLE";
    private static final String ACTION_REFRESH = "com.autosecretary.widget.REFRESH";
    private static final String ACTION_PREV_DAY = "com.autosecretary.widget.PREV_DAY";
    private static final String ACTION_NEXT_DAY = "com.autosecretary.widget.NEXT_DAY";

    static final String EXTRA_ACTION = "widget_action";
    static final String EXTRA_TASK_ID = "widget_task_id";
    static final String EXTRA_SLOT_ID = "widget_slot_id";

    private static final String PREFS_NAME = "widget_prefs";
    private static final String KEY_OFFSET = "selected_day_offset";
    private static final int MAX_OFFSET = 6;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive action=" + intent.getAction());
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) {
            // Check fill-in intent action
            action = intent.getStringExtra(EXTRA_ACTION);
        }
        if (action == null) return;

        switch (action) {
            case ACTION_PREV_DAY:
                navigateDay(context, -1);
                break;
            case ACTION_NEXT_DAY:
                navigateDay(context, 1);
                break;
            case ACTION_REFRESH:
                notifyWidgetUpdate(context);
                break;
            case ACTION_TOGGLE:
                handleToggle(context, intent);
                break;
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.task_list_widget);

        // Day label
        int offset = getSelectedDayOffset(context);
        LocalDate selectedDate = LocalDate.now().plusDays(offset);
        boolean isToday = offset == 0;
        String label = isToday ? "Heute" : selectedDate.format(DATE_FORMAT);
        Log.d(TAG, "updateWidget offset=" + offset + " label=" + label, new Throwable("caller"));
        views.setTextViewText(R.id.widget_date_label, label);

        // Arrow states
        views.setFloat(R.id.widget_prev_day, "setAlpha", isToday ? 0.3f : 1.0f);
        views.setFloat(R.id.widget_next_day, "setAlpha", offset >= MAX_OFFSET ? 0.3f : 1.0f);

        // Day navigation intents
        views.setOnClickPendingIntent(R.id.widget_prev_day,
                buildActionIntent(context, ACTION_PREV_DAY, widgetId));
        views.setOnClickPendingIntent(R.id.widget_next_day,
                buildActionIntent(context, ACTION_NEXT_DAY, widgetId));

        // Refresh intent
        views.setOnClickPendingIntent(R.id.widget_refresh,
                buildActionIntent(context, ACTION_REFRESH, widgetId));

        // Date label click opens the app
        Intent launchApp = new Intent(context, MainActivity.class);
        launchApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent launchPending = PendingIntent.getActivity(context, 0, launchApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_date_label, launchPending);

        // List adapter
        Intent serviceIntent = new Intent(context, TaskWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_task_list, serviceIntent);
        views.setEmptyView(R.id.widget_task_list, R.id.widget_empty);

        // Fill-in intent template for list item clicks (toggle checkbox)
        Intent toggleTemplate = new Intent(context, TaskWidgetProvider.class);
        toggleTemplate.setAction(ACTION_TOGGLE);
        PendingIntent templatePending = PendingIntent.getBroadcast(context, widgetId, toggleTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_task_list, templatePending);

        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_task_list);
    }

    private PendingIntent buildActionIntent(Context context, String action, int widgetId) {
        Intent intent = new Intent(context, TaskWidgetProvider.class);
        intent.setAction(action);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        // Unique data URI so PendingIntents don't collapse
        intent.setData(Uri.parse("widget://" + action + "/" + widgetId));
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void navigateDay(Context context, int delta) {
        int offset = getSelectedDayOffset(context);
        int newOffset = offset + delta;
        Log.d(TAG, "navigateDay old=" + offset + " delta=" + delta + " new=" + newOffset);
        if (newOffset < 0 || newOffset > MAX_OFFSET) return;
        setSelectedDayOffset(context, newOffset);
        notifyWidgetUpdate(context);
    }

    private void handleToggle(Context context, Intent intent) {
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        String slotId = intent.getStringExtra(EXTRA_SLOT_ID);
        if (taskId == null || slotId == null) return;

        // Use goAsync to get time for background DB work
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                TaskDAO dao = db.taskDao();
                Task task = dao.read(taskId);
                if (task == null) return;

                TaskSlot slot = null;
                for (TaskSlot s : task.slots) {
                    if (slotId.equals(s.id)) {
                        slot = s;
                        break;
                    }
                }
                if (slot == null) return;

                TaskCompletionService completionService = new TaskCompletionService();
                TaskLifecycleManager lifecycleManager = new TaskLifecycleManager();
                TaskCompletionService.CompletionPhase phase =
                        completionService.checkOff(task, slot, lifecycleManager);

                if (phase == TaskCompletionService.CompletionPhase.NONE) return;
                if (phase == TaskCompletionService.CompletionPhase.COMPLETED) {
                    dao.write(task);
                }
                dao.writeSlot(slot);
                notifyWidgetUpdate(context);
            } catch (Exception e) {
                Log.e(TAG, "Toggle failed", e);
            } finally {
                result.finish();
            }
        }).start();
    }

    // --- Day offset persistence ---

    static int getSelectedDayOffset(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_OFFSET, 0);
    }

    private static void setSelectedDayOffset(Context context, int offset) {
        // commit() instead of apply() to prevent race with subsequent onUpdate
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_OFFSET, offset).commit();
    }

    public static LocalDate getSelectedDate(Context context) {
        return LocalDate.now().plusDays(getSelectedDayOffset(context));
    }

    public static boolean isShowingToday(Context context) {
        return getSelectedDayOffset(context) == 0;
    }

    // --- Public refresh trigger ---

    public static void notifyWidgetUpdate(Context context) {
        Log.d(TAG, "notifyWidgetUpdate called", new Throwable("caller"));
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, TaskWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(widget);
        if (widgetIds.length > 0) {
            Intent updateIntent = new Intent(context, TaskWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
            context.sendBroadcast(updateIntent);
        }
    }
}
