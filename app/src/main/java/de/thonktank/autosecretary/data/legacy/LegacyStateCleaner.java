package de.thonktank.autosecretary.data.legacy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import de.thonktank.autosecretary.ReminderReceiver;
import de.thonktank.autosecretary.infrastructure.AppLogger;

import org.json.JSONArray;
import org.json.JSONObject;

public final class LegacyStateCleaner {
    private static final String TAG = "LegacyStateCleaner";
    private static final String MARKER_PREFS = "stability_refactor";
    private static final String DONE = "legacy_reset_done";
    private static final String NOTICE = "show_reset_notice";

    private final Context context;
    private final AppLogger logger;

    public LegacyStateCleaner(Context context, AppLogger logger) {
        this.context = context.getApplicationContext();
        this.logger = logger;
    }

    public void cleanOnce() {
        SharedPreferences marker = context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE);
        if (marker.getBoolean(DONE, false)) return;
        SharedPreferences old = context.getSharedPreferences("jetzt_state", Context.MODE_PRIVATE);
        cancelLegacyAlarms(old.getString("tasks", "[]"));
        context.deleteSharedPreferences("jetzt_state");
        marker.edit().putBoolean(DONE, true).putBoolean(NOTICE, true).apply();
        logger.info(TAG, "Legacy prototype state was removed once");
    }

    public boolean shouldShowResetNotice() {
        return context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
                .getBoolean(NOTICE, false);
    }

    public void acknowledgeResetNotice() {
        context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(NOTICE, false).apply();
    }

    private void cancelLegacyAlarms(String rawTasks) {
        try {
            JSONArray tasks = new JSONArray(rawTasks);
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) continue;
                String id = task.optString("id");
                if (id.isEmpty()) continue;
                Intent intent = new Intent(context, ReminderReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarms.cancel(pending);
                    pending.cancel();
                }
            }
        } catch (Exception error) {
            logger.error(TAG, "Could not parse or cancel legacy alarms", error);
        }
    }
}
