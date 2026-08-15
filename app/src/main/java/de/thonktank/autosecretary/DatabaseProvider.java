package de.thonktank.autosecretary;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.room.Room;

import org.json.JSONArray;
import org.json.JSONObject;

/** One-time clean break from the prototype storage and its alarms. */
final class DatabaseProvider {
    private static final String MARKER_PREFS = "stability_refactor";
    private static final String DONE = "legacy_reset_done";
    private static volatile AppDatabase instance;
    static AppDatabase get(Context context) {
        if (instance == null) synchronized (DatabaseProvider.class) {
            if (instance == null) {
                Context app = context.getApplicationContext();
                clearPrototypeStateOnce(app);
                instance = Room.databaseBuilder(app, AppDatabase.class, "auto_secretary.db").build();
            }
        }
        return instance;
    }
    static boolean wasReset(Context context) { return context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE).getBoolean("show_reset_notice", false); }
    static void acknowledgeReset(Context context) { context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE).edit().putBoolean("show_reset_notice", false).apply(); }
    private static void clearPrototypeStateOnce(Context context) {
        SharedPreferences marker = context.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE);
        if (marker.getBoolean(DONE, false)) return;
        SharedPreferences old = context.getSharedPreferences("jetzt_state", Context.MODE_PRIVATE);
        cancelLegacyAlarms(context, old.getString("tasks", "[]"));
        context.deleteSharedPreferences("jetzt_state");
        marker.edit().putBoolean(DONE, true).putBoolean("show_reset_notice", true).apply();
    }
    private static void cancelLegacyAlarms(Context context, String rawTasks) {
        try {
            JSONArray tasks = new JSONArray(rawTasks); AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < tasks.length(); i++) { JSONObject task = tasks.optJSONObject(i); if (task == null) continue;
                String id = task.optString("id"); if (id.isEmpty()) continue;
                Intent intent = new Intent(context, ReminderReceiver.class);
                PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) { alarms.cancel(pending); pending.cancel(); }
            }
        } catch (Exception ignored) { }
    }
}
