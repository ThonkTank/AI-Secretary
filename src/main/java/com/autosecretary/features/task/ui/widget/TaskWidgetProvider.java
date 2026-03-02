package com.autosecretary.features.task.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.MainActivity;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.shared.WidgetConfiguration;
import com.autosecretary.shared.ui.UiConstants;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalDate;

/**
 * {@link AppWidgetProvider} entry point for the task widget. Manages the widget lifecycle
 * (onUpdate, onReceive), user interactions (day navigation, checkbox toggle, refresh),
 * and view updates.
 *
 * <p><b>Key responsibilities:</b>
 * <ul>
 *   <li>Lifecycle: {@link #onUpdate(Context, AppWidgetManager, int[])} updates all widget instances</li>
 *   <li>Interaction: {@link #onReceive(Context, Intent)} dispatches user actions (prev/next day, toggle)</li>
 *   <li>Persistence: Selected day offset stored in SharedPreferences, survives app restart</li>
 *   <li>Async toggle: {@link #handleToggle(Context, Intent)} uses {@code goAsync()} to extend
 *       broadcast lifetime while the task mutation completes</li>
 * </ul>
 *
 * <p>See {@link README.md} for architecture and data flow.
 */
public class TaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TaskWidget";

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

    /** Called by the system on widget refresh; builds RemoteViews and wires intents for each instance. */
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    /** Dispatches user actions: day navigation (prev/next), refresh, and checkbox toggle. */
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
            case ACTION_PREV_DAY -> navigateDay(context, -1);
            case ACTION_NEXT_DAY -> navigateDay(context, 1);
            case ACTION_REFRESH -> notifyWidgetUpdate(context);
            case ACTION_TOGGLE -> handleToggle(context, intent);
            default -> {}
        }
    }

    /** Builds the widget's RemoteViews: date label, nav arrows, action buttons, and list adapter. */
    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.task_list_widget);

        // Day label
        int offset = getSelectedDayOffset(context);
        LocalDate selectedDate = LocalDate.now().plusDays(offset);
        boolean isToday = offset == 0;
        String label = isToday ? context.getString(R.string.task_list_day_nav_today) : selectedDate.format(DateFormatters.DAY_NAV_LABEL);
        Log.d(TAG, "updateWidget offset=" + offset + " label=" + label);
        views.setTextViewText(R.id.WidgetDateLabel, label);

        // Arrow states
        views.setFloat(R.id.WidgetPrevDay, "setAlpha", isToday ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);
        views.setFloat(R.id.WidgetNextDay, "setAlpha", offset >= MAX_OFFSET ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);

        // Day navigation intents
        views.setOnClickPendingIntent(R.id.WidgetPrevDay,
                buildActionIntent(context, ACTION_PREV_DAY, widgetId));
        views.setOnClickPendingIntent(R.id.WidgetNextDay,
                buildActionIntent(context, ACTION_NEXT_DAY, widgetId));

        // Refresh intent
        views.setOnClickPendingIntent(R.id.WidgetRefresh,
                buildActionIntent(context, ACTION_REFRESH, widgetId));

        // Add task intent
        PendingIntent addTaskPending = buildMainActivityIntent(context, widgetId,
                intent -> intent.putExtra(EXTRA_OPEN_TASK_FLOW, true));
        views.setOnClickPendingIntent(R.id.WidgetAdd, addTaskPending);

        // Date label click opens the app
        PendingIntent launchPending = buildMainActivityIntent(context, 0, intent -> {});
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

    /**
     * Builds a PendingIntent for launching MainActivity with custom intent configuration.
     * Centralizes common intent setup: flags, PendingIntent creation.
     *
     * @param context The context
     * @param requestId Request ID for PendingIntent; used for the "add task" flow (widgetId) or 0 (simple app launch)
     * @param customizer Lambda to add action, extras, or other customization to the intent
     */
    private PendingIntent buildMainActivityIntent(Context context, int requestId,
            java.util.function.Consumer<Intent> customizer) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        customizer.accept(intent);
        return PendingIntent.getActivity(context, requestId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Adjusts the day offset by delta (±1), clamps to [0, MAX_OFFSET], persists, and refreshes. */
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

        // goAsync() extends the broadcast receiver's lifetime beyond the ~6-second Android timeout.
        // Without it, onReceive() returns and the process may be killed before the database
        // mutation completes.
        PendingResult result = goAsync();
        AutoSecretaryApplication app = AutoSecretaryApplication.from(context);
        app.getAppCompositionRoot().getSharedExecutor().execute(() -> {
            try {
                app.getAppCompositionRoot().getTaskSlotToggleMutation()
                        .execute(taskId, slotId, () -> notifyWidgetUpdate(context), null);
            } catch (Exception e) {
                Log.e(TAG, "Toggle failed", e);
            } finally {
                result.finish();
            }
        });
    }

    // --- Day offset persistence ---

    private static SharedPreferences getWidgetPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static int getSelectedDayOffset(Context context) {
        return getWidgetPrefs(context).getInt(KEY_OFFSET, 0);
    }

    private static void setSelectedDayOffset(Context context, int offset) {
        // commit() instead of apply() to prevent race with subsequent onUpdate
        getWidgetPrefs(context).edit().putInt(KEY_OFFSET, offset).commit();
    }

    public static LocalDate getSelectedDate(Context context) {
        return LocalDate.now().plusDays(getSelectedDayOffset(context));
    }

    // --- Public refresh trigger ---

    public static void notifyWidgetUpdate(Context context) {
        Log.d(TAG, "notifyWidgetUpdate called");
        WidgetConfiguration.notifyUpdate(context, TaskWidgetProvider.class);
    }
}
