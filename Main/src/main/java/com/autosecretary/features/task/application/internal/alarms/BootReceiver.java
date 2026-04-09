package com.autosecretary.features.task.application.internal.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Triggered when the device boots up (receives ACTION_BOOT_COMPLETED broadcast).
 *
 * Purpose: Re-register the daily planning alarm after device restart.
 * Why needed: Android clears all alarms when the device is powered off. This receiver
 * ensures the daily schedule regeneration alarm is re-registered when the user restarts
 * their phone, so the task scheduling system continues to work.
 *
 * See README.md for the overall daily scheduling workflow.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        DailyPlanningScheduler.scheduleDaily(context);
    }
}
