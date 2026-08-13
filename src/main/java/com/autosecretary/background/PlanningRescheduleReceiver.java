package com.autosecretary.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the wall-clock-aligned run after reboot or clock configuration changes. */
public final class PlanningRescheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_DATE_CHANGED.equals(action)) {
            return;
        }
        com.autosecretary.app.AutoSecretaryApplication.from(context).scheduleBackground();
    }
}
