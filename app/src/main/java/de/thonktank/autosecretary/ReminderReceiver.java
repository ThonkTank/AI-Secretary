package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        ReminderScheduler.show(context, title == null ? "Deine nächste Aufgabe wartet" : title);
        // A reminder is deliberately a gentle recurring invitation, never a nag loop.
        ReminderScheduler.schedule(context, intent.getStringExtra("task_id"), title, intent.getStringExtra("slot"));
    }
}
