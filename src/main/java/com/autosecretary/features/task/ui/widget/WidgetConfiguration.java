package com.autosecretary.features.task.ui.widget;

/**
 * Shared configuration constants for app widgets (task and budget).
 * Keep these in sync with the corresponding values in widget_task_info.xml and widget_budget_info.xml.
 *
 * WARNING: The XML files cannot reference Java constants directly (Android limitation).
 * If WIDGET_UPDATE_PERIOD_MILLIS is changed here, you MUST manually update both:
 * - src/main/res/xml/widget_task_info.xml (android:updatePeriodMillis attribute)
 * - src/main/res/xml/widget_budget_info.xml (android:updatePeriodMillis attribute)
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
}
