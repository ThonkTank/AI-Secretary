package com.autosecretary.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.widget.FocusWidgetProvider;

/** Configured-day-start plan refresh that always schedules the following occurrence. */
public final class DailyPlanningWorker extends Worker {
    public DailyPlanningWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            AutoSecretaryApplication app = AutoSecretaryApplication.from(getApplicationContext());
            var graph = app.graph();
            graph.executors().callDatabase(() ->
                    graph.planFocus().execute(Integer.MAX_VALUE, true));
            FocusWidgetProvider.refreshAll(getApplicationContext());
            app.scheduleBackground();
            return Result.success();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (RuntimeException | java.util.concurrent.ExecutionException error) {
            if (getRunAttemptCount() < 3) return Result.retry();
            AutoSecretaryApplication.from(getApplicationContext()).scheduleBackground();
            return Result.failure();
        }
    }
}
