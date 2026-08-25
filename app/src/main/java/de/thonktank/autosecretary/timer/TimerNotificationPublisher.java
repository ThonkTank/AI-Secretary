package de.thonktank.autosecretary.timer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import de.thonktank.autosecretary.MainActivity;
import de.thonktank.autosecretary.R;

public final class TimerNotificationPublisher {
    public static final String CHANNEL_ID = "task_timers";
    private final Context context;
    private final NotificationManager notifications;

    public TimerNotificationPublisher(Context context) {
        this.context = context.getApplicationContext();
        this.notifications = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.timer_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.timer_notification_channel_description));
        channel.enableVibration(true);
        notifications.createNotificationChannel(channel);
    }

    public boolean notificationsAvailable() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean publish(TimerSession session) {
        if (!notificationsAvailable()) return false;
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, session.notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean rest = session.kind == TimerSession.Kind.REST;
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setContentTitle(context.getString(rest
                        ? R.string.rest_timer_notification_title
                        : R.string.duration_timer_notification_title))
                .setContentText(context.getString(rest
                        ? R.string.rest_timer_notification_text
                        : R.string.duration_timer_notification_text, session.title))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        notifications.notify(session.notificationId, notification);
        return true;
    }

    public void cancel(TimerSession session) {
        notifications.cancel(session.notificationId);
    }
}
