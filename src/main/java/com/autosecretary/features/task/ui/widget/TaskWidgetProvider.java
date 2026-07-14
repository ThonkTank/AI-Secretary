package com.autosecretary.features.task.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.shared.WidgetConfiguration;

import java.util.List;

/**
 * {@link AppWidgetProvider} entry point for the task widget. The widget shows a flat,
 * priority-sorted list of open tasks (no day scoping), optionally filtered to one category.
 *
 * <p><b>Key responsibilities:</b>
 * <ul>
 *   <li>Lifecycle: {@link #onUpdate} rebuilds all widget instances.</li>
 *   <li>Interaction: {@link #onReceive} dispatches category cycling, refresh, and checkbox toggle.</li>
 *   <li>Persistence: the selected category id (+ display label) is stored in SharedPreferences.</li>
 *   <li>Search: a button deep-links into the app (RemoteViews cannot host a real search field).</li>
 * </ul>
 *
 * <p>See {@link README.md} for architecture and data flow.
 */
public class TaskWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TaskWidget";

    static final String ACTION_TOGGLE = "com.autosecretary.widget.TOGGLE";
    private static final String ACTION_REFRESH = "com.autosecretary.widget.REFRESH";
    private static final String ACTION_CYCLE_CATEGORY = "com.autosecretary.widget.CYCLE_CATEGORY";
    public static final String ACTION_ADD_TASK = "com.autosecretary.widget.ADD_TASK";
    public static final String EXTRA_OPEN_TASK_FLOW = "widget_open_task_flow";
    public static final String EXTRA_FOCUS_TASK_SEARCH = "widget_focus_task_search";

    static final String EXTRA_ACTION = "widget_action";
    static final String EXTRA_TASK_ID = "widget_task_id";
    static final String EXTRA_SLOT_ID = "widget_slot_id";

    private static final String PREFS_NAME = "widget_prefs";
    private static final String KEY_CATEGORY_ID = "selected_category_id";
    private static final String KEY_CATEGORY_LABEL = "selected_category_label";

    /** Called by the system on widget refresh; builds RemoteViews and wires intents for each instance. */
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    /** Dispatches user actions: category cycling, refresh, and checkbox toggle. */
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
            case ACTION_CYCLE_CATEGORY -> cycleCategory(context);
            case ACTION_REFRESH -> notifyWidgetUpdate(context);
            case ACTION_TOGGLE -> handleToggle(context, intent);
            default -> {}
        }
    }

    /** Builds the widget's RemoteViews: category filter label, action buttons, and list adapter. */
    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.task_list_widget);

        views.setTextViewText(R.id.WidgetCategoryFilter, getSelectedCategoryLabel(context));

        // Category filter cycles on tap.
        views.setOnClickPendingIntent(R.id.WidgetCategoryFilter,
                buildActionIntent(context, ACTION_CYCLE_CATEGORY, widgetId));

        // Refresh intent
        views.setOnClickPendingIntent(R.id.WidgetRefresh,
                buildActionIntent(context, ACTION_REFRESH, widgetId));

        // Search deep-links into the app (RemoteViews cannot host an EditText).
        PendingIntent searchPending = buildMainActivityIntent(context, widgetId,
                intent -> intent.putExtra(EXTRA_FOCUS_TASK_SEARCH, true));
        views.setOnClickPendingIntent(R.id.WidgetSearch, searchPending);

        // Add task intent
        PendingIntent addTaskPending = buildMainActivityIntent(context, widgetId + 1,
                intent -> intent.putExtra(EXTRA_OPEN_TASK_FLOW, true));
        views.setOnClickPendingIntent(R.id.WidgetAdd, addTaskPending);

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
     *
     * @param requestId distinct request id per action so PendingIntents don't collapse
     * @param customizer adds action/extras to the intent
     */
    private PendingIntent buildMainActivityIntent(Context context, int requestId,
            java.util.function.Consumer<Intent> customizer) {
        Intent intent = launchIntent(context);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        customizer.accept(intent);
        return PendingIntent.getActivity(context, requestId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent launchIntent(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        return intent != null ? intent : new Intent();
    }

    /**
     * Advances the selected category filter: All → first category → … → last → All.
     * Runs on the DB executor since it reads the category list, then persists and refreshes.
     */
    private void cycleCategory(Context context) {
        PendingResult result = goAsync();
        AutoSecretaryApplication dependencies = AutoSecretaryApplication.from(context);
        dependencies.getDbExecutor().execute(() -> {
            try {
                LoadTaskWidgetItemsUseCase useCase = dependencies.createLoadTaskWidgetItemsUseCase();
                List<TaskCategory> categories = useCase.categories();
                String currentId = getSelectedCategoryId(context);

                String nextId;
                String nextLabel;
                if (LoadTaskWidgetItemsUseCase.CATEGORY_ALL.equals(currentId) || categories.isEmpty()) {
                    if (categories.isEmpty()) {
                        nextId = LoadTaskWidgetItemsUseCase.CATEGORY_ALL;
                        nextLabel = context.getString(R.string.task_widget_category_all);
                    } else {
                        nextId = categories.get(0).id;
                        nextLabel = categories.get(0).name;
                    }
                } else {
                    int index = indexOfCategory(categories, currentId);
                    if (index < 0 || index + 1 >= categories.size()) {
                        nextId = LoadTaskWidgetItemsUseCase.CATEGORY_ALL;
                        nextLabel = context.getString(R.string.task_widget_category_all);
                    } else {
                        nextId = categories.get(index + 1).id;
                        nextLabel = categories.get(index + 1).name;
                    }
                }
                setSelectedCategory(context, nextId, nextLabel);
                notifyWidgetUpdate(context);
            } catch (Exception e) {
                Log.e(TAG, "Category cycle failed", e);
            } finally {
                result.finish();
            }
        });
    }

    private static int indexOfCategory(List<TaskCategory> categories, String id) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void handleToggle(Context context, Intent intent) {
        String taskId = intent.getStringExtra(EXTRA_TASK_ID);
        String slotId = intent.getStringExtra(EXTRA_SLOT_ID);
        if (taskId == null || slotId == null) return;

        // goAsync() extends the broadcast receiver's lifetime beyond the ~6-second Android timeout.
        PendingResult result = goAsync();
        AutoSecretaryApplication dependencies = AutoSecretaryApplication.from(context);
        dependencies.getDbExecutor().execute(() -> {
            try {
                dependencies.getTaskSlotToggleMutation()
                        .execute(taskId, slotId, () -> notifyWidgetUpdate(context), null);
            } catch (Exception e) {
                Log.e(TAG, "Toggle failed", e);
            } finally {
                result.finish();
            }
        });
    }

    // --- Selected category persistence ---

    private static SharedPreferences getWidgetPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static String getSelectedCategoryId(Context context) {
        return getWidgetPrefs(context).getString(KEY_CATEGORY_ID, LoadTaskWidgetItemsUseCase.CATEGORY_ALL);
    }

    private static String getSelectedCategoryLabel(Context context) {
        return getWidgetPrefs(context).getString(KEY_CATEGORY_LABEL,
                context.getString(R.string.task_widget_category_all));
    }

    private static void setSelectedCategory(Context context, String id, String label) {
        // commit() instead of apply() to prevent race with the subsequent onUpdate refresh.
        getWidgetPrefs(context).edit()
                .putString(KEY_CATEGORY_ID, id)
                .putString(KEY_CATEGORY_LABEL, label)
                .commit();
    }

    // --- Public refresh trigger ---

    public static void notifyWidgetUpdate(Context context) {
        Log.d(TAG, "notifyWidgetUpdate called");
        WidgetConfiguration.notifyUpdate(context, TaskWidgetProvider.class);
    }
}
