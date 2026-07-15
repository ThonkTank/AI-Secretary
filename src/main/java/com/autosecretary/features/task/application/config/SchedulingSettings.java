package com.autosecretary.features.task.application.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;

/**
 * Persists the global scheduler settings in SharedPreferences: the "daily planning active"
 * toggle plus the buffer tuning (pause between tasks, lead time before appointments).
 * When planning is disabled, {@code RegenerateScheduleUseCase} clears the checklist and skips
 * slot generation, so the user can turn daily planning off without deleting any task data.
 * All values take effect on the next schedule regeneration.
 */
public final class SchedulingSettings {
    private static final String PREFS_NAME = "task_scheduling_prefs";
    private static final String KEY_ENABLED = "scheduling_enabled";
    private static final String KEY_SLOT_PAUSE_MINUTES = "slot_pause_minutes";
    private static final String KEY_APPOINTMENT_LEAD_MINUTES = "appointment_lead_minutes";

    static final int DEFAULT_SLOT_PAUSE_MINUTES = 10;
    static final int DEFAULT_APPOINTMENT_LEAD_MINUTES = 30;

    private SchedulingSettings() {}

    public static boolean isSchedulingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setSchedulingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getSlotPauseMinutes(Context context) {
        return prefs(context).getInt(KEY_SLOT_PAUSE_MINUTES, DEFAULT_SLOT_PAUSE_MINUTES);
    }

    public static void setSlotPauseMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_SLOT_PAUSE_MINUTES, Math.max(0, minutes)).apply();
    }

    public static int getAppointmentLeadMinutes(Context context) {
        return prefs(context).getInt(KEY_APPOINTMENT_LEAD_MINUTES, DEFAULT_APPOINTMENT_LEAD_MINUTES);
    }

    public static void setAppointmentLeadMinutes(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_APPOINTMENT_LEAD_MINUTES, Math.max(0, minutes)).apply();
    }

    /** The current buffer configuration as the domain value consumed by the slot generator. */
    public static SchedulingTuning tuning(Context context) {
        return new SchedulingTuning(getSlotPauseMinutes(context), getAppointmentLeadMinutes(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
