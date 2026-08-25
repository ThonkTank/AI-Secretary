package de.thonktank.autosecretary.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.thonktank.autosecretary.AutoSecretaryApplication;

public final class TimerBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        AutoSecretaryApplication.from(context).container().timers.reconcile(pending::finish);
    }
}
