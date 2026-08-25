package de.thonktank.autosecretary.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.thonktank.autosecretary.AutoSecretaryApplication;

public final class TimerAlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FINISH = "de.thonktank.autosecretary.TIMER_FINISH";
    public static final String EXTRA_TIMER_ID = "timer_id";

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent == null ? null : intent.getStringExtra(EXTRA_TIMER_ID);
        if (id == null || id.isEmpty()) return;
        PendingResult pending = goAsync();
        AutoSecretaryApplication.from(context).container().timers.finish(id, pending::finish);
    }
}
