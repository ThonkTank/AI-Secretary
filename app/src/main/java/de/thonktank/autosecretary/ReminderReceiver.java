package de.thonktank.autosecretary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    /** Kept unregistered solely so the first refactor release can cancel old explicit alarms. */
    @Override public void onReceive(Context context, Intent intent) { }
}
