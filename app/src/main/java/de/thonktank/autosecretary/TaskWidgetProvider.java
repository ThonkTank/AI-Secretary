package de.thonktank.autosecretary;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Thin RemoteViews lifecycle adapter; all loading and rendering lives in the coordinator. */
public final class TaskWidgetProvider extends AppWidgetProvider {
    @Override public void onEnabled(Context context) {
        coordinator(context).reconcileInstalledWidgets();
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        PendingResult pending = goAsync();
        try {
            coordinator(context).update(manager, ids, pending::finish);
        } catch (RuntimeException error) {
            log(context, "Could not start widget update", error);
            pending.finish();
        }
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                    int id, Bundle options) {
        PendingResult pending = goAsync();
        try {
            coordinator(context).updateOne(manager, id, options, pending::finish);
        } catch (RuntimeException error) {
            log(context, "Could not start widget resize", error);
            pending.finish();
        }
    }

    @Override public void onDeleted(Context context, int[] appWidgetIds) {
        coordinator(context).reconcileInstalledWidgets();
    }

    @Override public void onDisabled(Context context) {
        coordinator(context).stopObserving();
    }

    private static WidgetUpdateCoordinator coordinator(Context context) {
        return AutoSecretaryApplication.from(context).container().widgetUpdates;
    }

    private static void log(Context context, String message, RuntimeException error) {
        try {
            AutoSecretaryApplication.from(context).container().logger
                    .error("TaskWidgetProvider", message, error);
        } catch (RuntimeException ignored) {
            android.util.Log.e("TaskWidgetProvider", message, error);
        }
    }
}
