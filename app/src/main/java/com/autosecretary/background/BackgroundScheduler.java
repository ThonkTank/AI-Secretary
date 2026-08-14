package com.autosecretary.background;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Registers the app's one periodic background refresh path. */
public final class BackgroundScheduler {
    static final String PERIODIC_WORK = "focus-periodic-refresh";

    private BackgroundScheduler() { }

    public static void install(Context context) {
        PeriodicWorkRequest refresh = new PeriodicWorkRequest.Builder(
                FocusRefreshWorker.class, 30, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, refresh);
    }
}
