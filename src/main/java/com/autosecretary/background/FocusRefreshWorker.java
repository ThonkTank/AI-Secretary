package com.autosecretary.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.widget.FocusWidgetProvider;

/** Rebuilds persisted planning output without requiring a foreground Activity. */
public final class FocusRefreshWorker extends Worker {
    public FocusRefreshWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        return refresh(getApplicationContext(), getRunAttemptCount());
    }

    public static Result refresh(Context context, int runAttemptCount) {
        try {
            var graph = AutoSecretaryApplication.from(context).graph();
            graph.executors().callDatabase(() ->
                    graph.planFocus().execute(Integer.MAX_VALUE, true));
            FocusWidgetProvider.refreshAll(context);
            return Result.success();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (RuntimeException | java.util.concurrent.ExecutionException error) {
            return runAttemptCount < 3 ? Result.retry() : Result.failure();
        }
    }
}
