package de.thonktank.autosecretary;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import de.thonktank.autosecretary.domain.usecase.ActivateReadyFlows;
import de.thonktank.autosecretary.infrastructure.AppLogger;

import java.util.concurrent.TimeUnit;

/** Maintains exactly one best-effort wakeup for the earliest durable flow timestamp. */
public final class FlowWakeScheduler {
    static final String UNIQUE_WORK = "step-flow-earliest-ready";
    private static final String TAG = "FlowWakeScheduler";

    private final Context context;
    private final WorkManager workManager;
    private final ActivateReadyFlows flows;
    private final AppLogger logger;

    public FlowWakeScheduler(Context context, ActivateReadyFlows flows, AppLogger logger) {
        this.context = context.getApplicationContext();
        this.workManager = null;
        this.flows = flows;
        this.logger = logger;
    }

    FlowWakeScheduler(WorkManager workManager, ActivateReadyFlows flows, AppLogger logger) {
        this.context = null;
        this.workManager = workManager;
        this.flows = flows;
        this.logger = logger;
    }

    public void reschedule() {
        try {
            Long readyAt = flows.nextReadyAtEpochMillis();
            WorkManager manager = workManager == null
                    ? WorkManager.getInstance(context) : workManager;
            if (readyAt == null) {
                manager.cancelUniqueWork(UNIQUE_WORK);
                return;
            }
            long delay = initialDelay(System.currentTimeMillis(), readyAt);
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FlowWakeWorker.class)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build();
            manager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
        } catch (RuntimeException error) {
            logger.error(TAG, "Could not schedule the best-effort flow wakeup", error);
        }
    }

    static long initialDelay(long nowEpochMillis, long readyAtEpochMillis) {
        return Math.max(0L, readyAtEpochMillis - nowEpochMillis);
    }
}
