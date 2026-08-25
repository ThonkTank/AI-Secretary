package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Async lifecycle adapter for validated, non-destructive widget actions. */
public final class TaskActionReceiver extends BroadcastReceiver {
    public static final String COMPLETE = "de.thonktank.autosecretary.COMPLETE";
    public static final String LATER = "de.thonktank.autosecretary.LATER";
    public static final String TOGGLE_STEP = "de.thonktank.autosecretary.TOGGLE_STEP";
    public static final String EXTRA_OCCURRENCE_ID = "occurrence_id";
    public static final String EXTRA_STEP_ID = "step_id";

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        Intent stableIntent = intent == null ? null : new Intent(intent);
        try {
            AppContainer container = AutoSecretaryApplication.from(app).container();
            container.executors.widgetSerial.execute(() -> {
                try {
                    new TaskActionHandler(container).handle(stableIntent);
                } catch (RuntimeException error) {
                    container.logger.error("TaskActionReceiver",
                            "Widget action failed: " + (stableIntent == null
                                    ? null : stableIntent.getAction()),
                            error);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException error) {
            try {
                AutoSecretaryApplication.from(app).container().logger.error("TaskActionReceiver",
                        "Could not schedule widget action", error);
            } catch (RuntimeException loggingError) {
                android.util.Log.e("TaskActionReceiver", "Could not schedule widget action", error);
            } finally {
                pending.finish();
            }
        }
    }
}
