package com.autosecretary.ui;

import android.os.Handler;
import android.os.Looper;

import com.autosecretary.application.TimeProvider;

import java.time.Duration;
import java.time.Instant;

/** Lifecycle-bound timer for the canonical timeline's next semantic boundary. */
final class TimelineRefreshScheduler implements AutoCloseable {
    private final TimeProvider time;
    private final Runnable refresh;
    private final Handler handler;
    private final Runnable dispatch;
    private Instant target;
    private boolean started;

    TimelineRefreshScheduler(TimeProvider time, Runnable refresh) {
        this(time, refresh, new Handler(Looper.getMainLooper()));
    }

    TimelineRefreshScheduler(TimeProvider time, Runnable refresh, Handler handler) {
        this.time = time;
        this.refresh = refresh;
        this.handler = handler;
        dispatch = () -> {
            if (!started) return;
            target = null;
            refresh.run();
        };
    }

    void update(Instant nextRefreshAt) {
        if (nextRefreshAt == null || nextRefreshAt.equals(target)) return;
        target = nextRefreshAt;
        schedule();
    }

    void start() {
        if (started) return;
        started = true;
        schedule();
    }

    void stop() {
        started = false;
        handler.removeCallbacks(dispatch);
    }

    static long delayMillis(TimeProvider time, Instant target) {
        return Math.max(1, Duration.between(time.now(), target).toMillis());
    }

    private void schedule() {
        handler.removeCallbacks(dispatch);
        if (started && target != null) {
            handler.postDelayed(dispatch, delayMillis(time, target));
        }
    }

    @Override public void close() {
        stop();
        target = null;
    }
}
