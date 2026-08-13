package com.autosecretary.platform;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/** Activity-scoped, permission-aware and debounced calendar invalidation source. */
public final class CalendarChangeObserver implements AutoCloseable {
    private static final long DEBOUNCE_MS = 750;

    private final Context context;
    private final Executor executor;
    private final Runnable refresh;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dispatch;
    private final ContentObserver observer;
    private boolean registered;

    public CalendarChangeObserver(Context context, Executor executor, Runnable refresh) {
        this.context = context.getApplicationContext();
        this.executor = executor;
        this.refresh = refresh;
        dispatch = () -> this.executor.execute(this.refresh);
        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                handler.removeCallbacks(dispatch);
                handler.postDelayed(dispatch, DEBOUNCE_MS);
            }
        };
    }

    public synchronized void start() {
        boolean permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        if (permitted && !registered) {
            context.getContentResolver().registerContentObserver(
                    CalendarContract.Events.CONTENT_URI, true, observer);
            registered = true;
        }
    }

    public synchronized void stop() {
        if (registered) context.getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacks(dispatch);
        registered = false;
    }

    public synchronized boolean isRegistered() { return registered; }

    @Override
    public synchronized void close() {
        stop();
        handler.removeCallbacksAndMessages(null);
    }
}
