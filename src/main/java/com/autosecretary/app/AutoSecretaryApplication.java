package com.autosecretary.app;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.autosecretary.features.task.application.internal.alarms.DailyPlanningScheduler;
import com.autosecretary.features.task.ui.widget.TaskWidgetProvider;

/**
 * Application subclass — the real entry point of the app.
 *
 * <p>Android creates exactly one instance of this class per process lifetime, before any
 * Activity or BroadcastReceiver. {@link #onCreate()} is the right place for one-time
 * setup that must happen before the UI starts.</p>
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Create {@link AppCompositionRoot} — the app's manual DI root.</li>
 *   <li>Schedule the daily task-planning alarm via {@link DailyPlanningScheduler}.</li>
 *   <li>Register a receiver that refreshes the home screen widget when the user unlocks.</li>
 * </ol>
 *
 * <h2>Accessing the composition root</h2>
 * Any component that holds a {@link Context} can reach the root with the convenience method:
 * <pre>
 *   AppCompositionRoot root = AutoSecretaryApplication.from(context).getAppCompositionRoot();
 * </pre>
 *
 * @see AppCompositionRoot
 */
public class AutoSecretaryApplication extends Application {
    private AppCompositionRoot appCompositionRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        appCompositionRoot = new AppCompositionRoot(this);
        DailyPlanningScheduler.scheduleDaily(this);
        registerWidgetRefreshOnUnlock();
    }

    /**
     * Registers a receiver for {@link Intent#ACTION_USER_PRESENT} (fired when the user dismisses
     * the lock screen) to refresh the task home-screen widget immediately on unlock.
     *
     * <p>Without this, the widget would only update on its scheduled interval. Refreshing on
     * unlock ensures the user sees current task data the moment they pick up their phone.</p>
     *
     * <p>{@link Context#RECEIVER_NOT_EXPORTED} is required for dynamically-registered receivers
     * on Android 13+ (API 33+) to restrict the broadcast to the app's own process.</p>
     */
    private void registerWidgetRefreshOnUnlock() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TaskWidgetProvider.notifyWidgetUpdate(context);
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    public AppCompositionRoot getAppCompositionRoot() {
        return appCompositionRoot;
    }

    /** Convenience accessor: cast the application context to this class. */
    public static AutoSecretaryApplication from(Context context) {
        return (AutoSecretaryApplication) context.getApplicationContext();
    }
}
