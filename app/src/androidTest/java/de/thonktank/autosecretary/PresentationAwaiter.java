package de.thonktank.autosecretary;

import android.app.Instrumentation;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Signal-driven AndroidTest condition wait with a diagnostic hard timeout. */
public final class PresentationAwaiter {
    private static final long TIMEOUT_MILLIS = 5_000L;

    private PresentationAwaiter() { }

    public static void await(Instrumentation instrumentation, String message,
                             BooleanSupplier condition, View... watchedViews) {
        if (instrumentation == null || condition == null)
            throw new IllegalArgumentException("Instrumentation and condition are required");
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable evaluate = () -> {
            if (!active.get() || completed.getCount() == 0) return;
            try {
                if (condition.getAsBoolean()) completed.countDown();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                completed.countDown();
            }
        };
        PresentationTrace.Subscription trace = PresentationTrace.subscribe(
                event -> main.post(evaluate));
        View.OnLayoutChangeListener layout = (view, left, top, right, bottom,
                                              oldLeft, oldTop, oldRight, oldBottom) ->
                evaluate.run();
        View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { evaluate.run(); }
            @Override public void onViewDetachedFromWindow(View view) { evaluate.run(); }
        };
        instrumentation.runOnMainSync(() -> {
            for (View view : watchedViews) {
                if (view == null) continue;
                view.addOnLayoutChangeListener(layout);
                view.addOnAttachStateChangeListener(attachment);
            }
            evaluate.run();
        });

        boolean signaled;
        try {
            signaled = completed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message + " (test thread interrupted)", interrupted);
        } finally {
            active.set(false);
            trace.close();
            instrumentation.runOnMainSync(() -> {
                for (View view : watchedViews) {
                    if (view == null) continue;
                    view.removeOnLayoutChangeListener(layout);
                    view.removeOnAttachStateChangeListener(attachment);
                }
            });
        }
        if (failure.get() != null) throw new AssertionError(message, failure.get());
        if (!signaled) throw new AssertionError(message + "\nPresentation trace:\n"
                + PresentationTrace.describe());
    }
}
