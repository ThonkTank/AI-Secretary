package com.autosecretary.shared;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Shared configuration constants for app widgets (task and budget).
 *
 * <p>The Android framework cannot reference Java constants directly in widget configuration XML
 * files. A Gradle verification task checks that {@code WIDGET_UPDATE_PERIOD_MILLIS} stays in sync
 * with the {@code android:updatePeriodMillis} attribute in both widget XML files:
 * <ul>
 *   <li><code>src/main/res/xml/widget_task_info.xml</code> (android:updatePeriodMillis)
 *   <li><code>src/main/res/xml/widget_budget_info.xml</code> (android:updatePeriodMillis)
 * </ul>
 * The build fails on mismatch so widget refresh behavior cannot silently diverge.
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
