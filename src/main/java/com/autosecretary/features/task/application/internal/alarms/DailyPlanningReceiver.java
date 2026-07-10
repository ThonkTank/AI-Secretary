package com.autosecretary.features.task.application.internal.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.WidgetRefreshNotifier;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;

/**
 * Triggered when the daily planning alarm fires (usually at midnight).
 *
 * Responsibilities:
 * 1. Re-schedule the next day's alarm (keeps the daily cycle going)
 * 2. Regenerate task slots for the upcoming day
 * 3. Update all widgets with the refreshed schedule
 *
 * Uses goAsync() pattern to hold the receiver alive while async work completes.
 * See README.md for the full daily scheduling workflow and goAsync pattern explanation.
 */
public class DailyPlanningReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyPlanningReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DailyPlanningScheduler.ACTION_DAILY_PLANNING_ALARM.equals(intent.getAction())) {
            return;
        }

        DailyPlanningScheduler.scheduleDaily(context);

        // goAsync() extends the receiver's lifetime until pendingResult.finish() is called.
        // Without this, Android would kill the receiver before the async work completes.
        // See README.md "Broadcast Receiver Lifecycle" for details.
        PendingResult pendingResult = goAsync();
        try {
            AutoSecretaryApplication application = AutoSecretaryApplication.from(context);
            WidgetRefreshNotifier widgetRefreshNotifier = application
                    .getAppCompositionRoot()
                    .getWidgetRefreshNotifier();
            RegenerateScheduleUseCase useCase = application
                    .getAppCompositionRoot()
                    .getRegenerateScheduleUseCase();

            // Regenerate daily slots: generates new TaskSlots for today based on TaskPrefSlots,
            // adapting from recent completion history (adaptive scheduling).
            // This ensures the task list always has fresh, personalized scheduling.
            useCase.execute(result -> {
                try {
                    widgetRefreshNotifier.refreshTaskWidgets();
                } catch (Exception e) {
                    Log.e(TAG, "Widget update failed after schedule regeneration", e);
                } finally {
                    pendingResult.finish();
                }
            });
        } catch (Exception exception) {
            Log.e(TAG, "Failed to regenerate schedule or update widgets", exception);
            pendingResult.finish();
        }
    }
}
