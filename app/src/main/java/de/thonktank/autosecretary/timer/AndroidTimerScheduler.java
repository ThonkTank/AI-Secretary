package de.thonktank.autosecretary.timer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public final class AndroidTimerScheduler implements TimerScheduler {
    private final Context context;
    private final AlarmManager alarms;

    public AndroidTimerScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarms = context.getSystemService(AlarmManager.class);
    }

    @Override public boolean schedule(TimerSession session) {
        PendingIntent alarm = pendingIntent(session);
        if (exactAlarmsAvailable()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        session.targetElapsedRealtime, alarm);
                return true;
            } catch (SecurityException ignored) {
                // Permission can be revoked between the capability check and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                session.targetElapsedRealtime, alarm);
        return false;
    }

    @Override public void cancel(TimerSession session) {
        alarms.cancel(pendingIntent(session));
    }

    @Override public boolean exactAlarmsAvailable() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
    }

    private PendingIntent pendingIntent(TimerSession session) {
        Intent intent = new Intent(context, TimerAlarmReceiver.class)
                .setAction(TimerAlarmReceiver.ACTION_FINISH)
                .setData(Uri.parse("autosecretary://timer/" + Uri.encode(session.id)))
                .putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, session.id);
        return PendingIntent.getBroadcast(context, session.notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
