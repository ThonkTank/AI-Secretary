package com.autosecretary.features.task.application.internal.scheduling;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.ZoneId;

public final class DailyPlanningScheduler {
    public static final String ACTION_DAILY_PLANNING_ALARM =
            "com.autosecretary.features.task.action.DAILY_PLANNING_ALARM";

    private static final int REQUEST_CODE_DAILY_PLANNING = 1001;

    private DailyPlanningScheduler() {
    }

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

        // On Android S+, check if the app has SCHEDULE_EXACT_ALARM permission.
        // If not, fall back to the less-precise setAndAllowWhileIdle method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnightMillis,
                    alarmIntent
            );
            return;
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextMidnightMillis,
                alarmIntent
        );
    }

}
