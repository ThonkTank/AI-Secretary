package com.autosecretary.features.task.application.internal.scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

public class DailyPlanningReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyPlanningReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DailyPlanningScheduler.ACTION_DAILY_PLANNING_ALARM.equals(intent.getAction())) {
            return;
        }

        DailyPlanningScheduler.scheduleDaily(context);

        PendingResult pendingResult = goAsync();
        try {
            AutoSecretaryApplication application = AutoSecretaryApplication.from(context);
            RegenerateScheduleUseCase useCase = application
                    .getAppCompositionRoot()
                    .getRegenerateScheduleUseCase();

            useCase.execute(result -> {
                TaskWidgetProvider.notifyWidgetUpdate(context);
                pendingResult.finish();
            });
        } catch (Exception exception) {
            Log.e(TAG, "Daily planning setup failed", exception);
            pendingResult.finish();
        }
    }
}
