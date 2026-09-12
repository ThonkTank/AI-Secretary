package de.thonktank.autosecretary.ui.today;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import de.thonktank.autosecretary.MainActivity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real product lifecycle with finite event waits, including while the window keeps animating. */
final class ProductActivitySession implements AutoCloseable, Application.ActivityLifecycleCallbacks {
    private static final long TIMEOUT_SECONDS = 10;
    private final Application application;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<MainActivity> activities = new LinkedHashSet<>(); // Main thread only.
    private final StringBuffer trace = new StringBuffer();
    private MainActivity previous;
    private volatile MainActivity resumed;
    private CountDownLatch resumeSignal;
    private CountDownLatch destroySignal;

    ProductActivitySession(Instrumentation instrumentation) {
        application = (Application) instrumentation.getTargetContext().getApplicationContext();
        onMain(() -> application.registerActivityLifecycleCallbacks(this));
    }

    MainActivity launch() {
        return resumeAfter(null, () -> application.startActivity(new Intent(application, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)));
    }

    MainActivity recreate() {
        MainActivity old = resumed;
        if (old == null) throw new AssertionError("No resumed product activity to recreate");
        return resumeAfter(old, old::recreate);
    }

    private MainActivity resumeAfter(MainActivity old, Runnable action) {
        CountDownLatch signal = new CountDownLatch(1);
        onMain(() -> {
            previous = old;
            resumed = null;
            resumeSignal = signal;
            action.run();
        });
        await(signal, old == null ? "Product launch" : "Product recreation");
        return resumed;
    }

    @Override public void close() {
        CountDownLatch signal = new CountDownLatch(1);
        try {
            onMain(() -> {
                destroySignal = signal;
                for (MainActivity activity : new ArrayList<>(activities)) activity.finish();
                if (activities.isEmpty()) signal.countDown();
            });
            await(signal, "Product activity destruction");
        } finally {
            onMain(() -> application.unregisterActivityLifecycleCallbacks(this));
        }
    }

    private void onMain(Runnable action) {
        CountDownLatch signal = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable dispatch = () -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { signal.countDown(); }
        };
        if (!main.post(dispatch)) throw new AssertionError("Product main thread rejected action");
        try { await(signal, "Product main-thread action"); }
        finally { main.removeCallbacks(dispatch); }
        if (failure.get() != null) throw new AssertionError("Product main-thread action failed", failure.get());
    }

    private void await(CountDownLatch signal, String operation) {
        try {
            if (!signal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                throw new AssertionError(operation + " timed out; lifecycle:\n" + trace);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(operation + " interrupted", interrupted);
        }
    }

    private void event(Activity activity, String stage) {
        if (activity instanceof MainActivity) {
            String message = stage + " " + Integer.toHexString(System.identityHashCode(activity));
            trace.append(message).append('\n');
            Log.d("ProductActivitySession", message);
        }
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (activity instanceof MainActivity) activities.add((MainActivity) activity);
        event(activity, "CREATED");
    }
    @Override public void onActivityResumed(Activity activity) {
        event(activity, "RESUMED");
        if (activity instanceof MainActivity && activity != previous && resumeSignal != null) {
            resumed = (MainActivity) activity;
            resumeSignal.countDown();
        }
    }
    @Override public void onActivityDestroyed(Activity activity) {
        event(activity, "DESTROYED");
        activities.remove(activity);
        if (activities.isEmpty() && destroySignal != null) destroySignal.countDown();
    }
    @Override public void onActivityStarted(Activity activity) { event(activity, "STARTED"); }
    @Override public void onActivityPaused(Activity activity) { event(activity, "PAUSED"); }
    @Override public void onActivityStopped(Activity activity) { event(activity, "STOPPED"); }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
