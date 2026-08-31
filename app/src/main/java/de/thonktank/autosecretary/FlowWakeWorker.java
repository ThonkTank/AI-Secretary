package de.thonktank.autosecretary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Best-effort wakeup; foreground reads always perform the same activation synchronously. */
public final class FlowWakeWorker extends Worker {
    public FlowWakeWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        AppContainer container = AutoSecretaryApplication.from(getApplicationContext()).container();
        try {
            container.flows.activateReadyFlows.execute();
            container.flowWakeScheduler.reschedule();
            return Result.success();
        } catch (RuntimeException error) {
            container.logger.error("FlowWakeWorker", "Could not activate ready flows", error);
            return Result.retry();
        }
    }
}
