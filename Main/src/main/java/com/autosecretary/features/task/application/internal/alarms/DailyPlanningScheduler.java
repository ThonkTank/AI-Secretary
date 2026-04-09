package com.autosecretary.features.task.application.internal.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Manages scheduling of the daily planning alarm using Android's AlarmManager.
 *
 * Schedules an alarm for next midnight (in device timezone) that triggers DailyPlanningReceiver.
 * On Android 12+ (S), checks whether SCHEDULE_EXACT_ALARM permission is available and falls back
 * to less-precise scheduling if needed. The app continues to work reliably with both.
 *
 * See README.md for context and public resource links.
 */
public final class DailyPlanningScheduler {
    public static final String ACTION_DAILY_PLANNING_ALARM =
            "com.autosecretary.features.task.action.DAILY_PLANNING_ALARM";

    private static final int REQUEST_CODE_DAILY_PLANNING = 1001;

    private DailyPlanningScheduler() {
    }

    /**
     * Schedules the next daily planning alarm for midnight (local device time).
     *
     * @param context Application context. Will use getApplicationContext() internally.
     */
    public static void scheduleDaily(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = appContext.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }

        long nextMidnightMillis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        PendingIntent alarmIntent = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE_DAILY_PLANNING,
                new Intent(appContext, DailyPlanningReceiver.class)
                        .setAction(ACTION_DAILY_PLANNING_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Android 12+ (API 31+) permission fallback:
        // - SCHEDULE_EXACT_ALARM permission allows precise midnight timing (preferred)
        // - If permission is not available, fall back to setAndAllowWhileIdle (less precise,
        //   but still reliable for daily scheduling)
        // - Android 11 and earlier: always use exact scheduling (permission doesn't exist)
        //
        // See: https://developer.android.com/reference/android/app/AlarmManager#canScheduleExactAlarms()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnightMillis,
                    alarmIntent
            );
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnightMillis,
                    alarmIntent
            );
        }
    }

}
