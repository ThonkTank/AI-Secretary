package com.autosecretary.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.autosecretary.widget.FocusWidgetProvider;

/** Invalidates widgets so their live plan is rebuilt without a foreground Activity. */
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
            FocusWidgetProvider.refreshAll(context);
            return Result.success();
        } catch (RuntimeException error) {
            return runAttemptCount < 3 ? Result.retry() : Result.failure();
        }
    }
}
