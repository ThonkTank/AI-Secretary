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
    private static final String KEY_MAX_WORK_MINUTES_PER_DAY = "max_work_minutes_per_day";
    private static final String KEY_MIN_LEISURE_MINUTES_PER_DAY = "min_leisure_minutes_per_day";

    static final int DEFAULT_SLOT_PAUSE_MINUTES = 10;
    static final int DEFAULT_APPOINTMENT_LEAD_MINUTES = 30;
    /** Balance budgets default to 0 = disabled — opt-in, no behaviour change until configured. */
    static final int DEFAULT_MAX_WORK_MINUTES_PER_DAY = 0;
    static final int DEFAULT_MIN_LEISURE_MINUTES_PER_DAY = 0;

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

    public static int getMaxWorkMinutesPerDay(Context context) {
        return prefs(context).getInt(KEY_MAX_WORK_MINUTES_PER_DAY, DEFAULT_MAX_WORK_MINUTES_PER_DAY);
    }

    public static void setMaxWorkMinutesPerDay(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_MAX_WORK_MINUTES_PER_DAY, Math.max(0, minutes)).apply();
    }

    public static int getMinLeisureMinutesPerDay(Context context) {
        return prefs(context).getInt(KEY_MIN_LEISURE_MINUTES_PER_DAY, DEFAULT_MIN_LEISURE_MINUTES_PER_DAY);
    }

    public static void setMinLeisureMinutesPerDay(Context context, int minutes) {
        prefs(context).edit().putInt(KEY_MIN_LEISURE_MINUTES_PER_DAY, Math.max(0, minutes)).apply();
    }

    /** The current tuning configuration as the domain value consumed by the slot generator. */
    public static SchedulingTuning tuning(Context context) {
        return new SchedulingTuning(
                getSlotPauseMinutes(context),
                getAppointmentLeadMinutes(context),
                getMaxWorkMinutesPerDay(context),
                getMinLeisureMinutesPerDay(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
