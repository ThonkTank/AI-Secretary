package com.autosecretary.background;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/** Central registration point for autonomous planning and widget freshness. */
public final class BackgroundScheduler {
    static final String DAILY_WORK = "focus-daily-plan";
    static final String PERIODIC_WORK = "focus-calendar-widget-refresh";

    private BackgroundScheduler() { }

    public static void install(Context context, LocalTime dayStart) {
        scheduleNextDaily(context, dayStart);
        PeriodicWorkRequest refresh = new PeriodicWorkRequest.Builder(
                FocusRefreshWorker.class, 30, TimeUnit.MINUTES)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, refresh);
    }

    public static void scheduleNextDaily(Context context, LocalTime dayStart) {
        ZonedDateTime now = ZonedDateTime.now();
        OneTimeWorkRequest daily = new OneTimeWorkRequest.Builder(DailyPlanningWorker.class)
                .setInitialDelay(delayUntilNext(now, dayStart))
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                DAILY_WORK, ExistingWorkPolicy.REPLACE, daily);
    }

    static Duration delayUntilNext(ZonedDateTime now, LocalTime dayStart) {
        ZonedDateTime next = now.toLocalDate().atTime(dayStart).atZone(now.getZone());
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(dayStart).atZone(now.getZone());
        }
        return Duration.between(now, next);
    }
}
