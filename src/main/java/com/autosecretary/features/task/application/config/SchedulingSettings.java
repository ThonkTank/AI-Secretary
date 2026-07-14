package com.autosecretary.features.task.application.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the global "daily planning active" toggle in SharedPreferences. When disabled,
 * {@code RegenerateScheduleUseCase} clears the checklist and skips slot generation, so the
 * user can turn daily planning off without deleting any task data. Defaults to enabled.
 */
public final class SchedulingSettings {
    private static final String PREFS_NAME = "task_scheduling_prefs";
    private static final String KEY_ENABLED = "scheduling_enabled";

    private SchedulingSettings() {}

    public static boolean isSchedulingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setSchedulingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
