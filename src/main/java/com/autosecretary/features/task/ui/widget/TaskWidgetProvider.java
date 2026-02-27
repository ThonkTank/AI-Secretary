package com.autosecretary.features.task.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.MainActivity;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.shared.WidgetConfiguration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TaskWidget";

    // Widget update period is defined in WidgetConfiguration and configured in widget_task_info.xml.
    // Both must be kept in sync.
    @SuppressWarnings("unused")
    private static final long WIDGET_UPDATE_PERIOD_MILLIS = WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS;

    static final String ACTION_TOGGLE = "com.autosecretary.widget.TOGGLE";
    private static final String ACTION_REFRESH = "com.autosecretary.widget.REFRESH";
    private static final String ACTION_PREV_DAY = "com.autosecretary.widget.PREV_DAY";
    private static final String ACTION_NEXT_DAY = "com.autosecretary.widget.NEXT_DAY";
    public static final String ACTION_ADD_TASK = "com.autosecretary.widget.ADD_TASK";
    public static final String EXTRA_OPEN_TASK_FLOW = "widget_open_task_flow";

    static final String EXTRA_ACTION = "widget_action";
    static final String EXTRA_TASK_ID = "widget_task_id";
    static final String EXTRA_SLOT_ID = "widget_slot_id";

    private static final String PREFS_NAME = "widget_prefs";
    private static final String KEY_OFFSET = "selected_day_offset";
    private static final int MAX_OFFSET = TaskViewModel.MAX_DAY_OFFSET;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

    private static final float ALPHA_ENABLED = 1.0f;
    private static final float ALPHA_DISABLED = 0.3f;

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
            action = intent.getStringExtra(EXTRA_ACTION);
        }
        if (action == null) {
            return;
        }

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
        String label = isToday ? context.getString(R.string.task_list_day_nav_today) : selectedDate.format(DATE_FORMATTER);
        Log.d(TAG, "updateWidget offset=" + offset + " label=" + label);
        views.setTextViewText(R.id.WidgetDateLabel, label);

        // Arrow states
        views.setFloat(R.id.WidgetPrevDay, "setAlpha", isToday ? ALPHA_DISABLED : ALPHA_ENABLED);
        views.setFloat(R.id.WidgetNextDay, "setAlpha", offset >= MAX_OFFSET ? ALPHA_DISABLED : ALPHA_ENABLED);

        // Day navigation intents
        views.setOnClickPendingIntent(R.id.WidgetPrevDay,
                buildActionIntent(context, ACTION_PREV_DAY, widgetId));
        views.setOnClickPendingIntent(R.id.WidgetNextDay,
                buildActionIntent(context, ACTION_NEXT_DAY, widgetId));

        // Refresh intent
        views.setOnClickPendingIntent(R.id.WidgetRefresh,
                buildActionIntent(context, ACTION_REFRESH, widgetId));

        // Add task intent
        Intent addTaskIntent = new Intent(context, MainActivity.class);
        addTaskIntent.setAction(ACTION_ADD_TASK);
        addTaskIntent.putExtra(EXTRA_OPEN_TASK_FLOW, true);
        addTaskIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent addTaskPending = PendingIntent.getActivity(context, widgetId, addTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.WidgetAdd, addTaskPending);

        // Date label click opens the app
        Intent launchApp = new Intent(context, MainActivity.class);
        launchApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent launchPending = PendingIntent.getActivity(context, 0, launchApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.WidgetDateLabel, launchPending);

        // List adapter
        Intent serviceIntent = new Intent(context, TaskWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.WidgetTaskList, serviceIntent);
        views.setEmptyView(R.id.WidgetTaskList, R.id.WidgetEmpty);

        // Fill-in intent template for list item clicks (toggle checkbox)
        Intent toggleTemplate = new Intent(context, TaskWidgetProvider.class);
        toggleTemplate.setAction(ACTION_TOGGLE);
        PendingIntent templatePending = PendingIntent.getBroadcast(context, widgetId, toggleTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.WidgetTaskList, templatePending);

        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.WidgetTaskList);
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

        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AutoSecretaryApplication app = AutoSecretaryApplication.from(context);
                app.getAppCompositionRoot().getTaskSlotToggleMutation()
                        .execute(taskId, slotId, () -> notifyWidgetUpdate(context), null);
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
        Log.d(TAG, "notifyWidgetUpdate called");
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
