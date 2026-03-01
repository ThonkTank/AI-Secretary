package com.autosecretary.shared;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Shared configuration constants for app widgets (task and budget).
 * Keep these in sync with the corresponding values in the widget configuration XML files.
 *
 * <strong>WARNING — Manual XML Synchronization Required:</strong> The Android framework
 * cannot reference Java constants directly in widget configuration XML files (framework limitation).
 * If {@code WIDGET_UPDATE_PERIOD_MILLIS} is changed here, you MUST manually update the
 * {@code android:updatePeriodMillis} attribute in both widget XML files:
 * <ul>
 *   <li><code>src/main/res/xml/widget_task_info.xml</code> (android:updatePeriodMillis)
 *   <li><code>src/main/res/xml/widget_budget_info.xml</code> (android:updatePeriodMillis)
 * </ul>
 * Failure to update both files means one widget will update at a different interval,
 * causing inconsistent widget refresh behavior.
 */
public final class WidgetConfiguration {
    private WidgetConfiguration() {
        // utility class
    }

    /**
     * Widget update period in milliseconds.
     * 1800000 ms = 30 minutes
     *
     * This is the minimum time between automatic widget refreshes requested by the system.
     * Both the task and budget widgets share this update interval to prevent excessive
     * database queries and system load.
     */
    public static final long WIDGET_UPDATE_PERIOD_MILLIS = 1800000L;

    /**
     * Send an {@link AppWidgetManager#ACTION_APPWIDGET_UPDATE} broadcast to all instances of
     * the given widget provider. Shared logic for {@code TaskWidgetProvider.notifyWidgetUpdate()}
     * and {@code BudgetWidgetProvider.notifyWidgetUpdate()}.
     *
     * @param context            application or activity context
     * @param widgetProviderClass the concrete {@link AppWidgetProvider} subclass to notify
     */
    public static void notifyUpdate(Context context, Class<? extends AppWidgetProvider> widgetProviderClass) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, widgetProviderClass);
        int[] widgetIds = manager.getAppWidgetIds(widget);
        if (widgetIds.length == 0) return;
        Intent updateIntent = new Intent(context, widgetProviderClass);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(updateIntent);
    }
}
