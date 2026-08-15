package de.thonktank.autosecretary;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDateTime;
import java.time.ZoneId;

final class ReminderScheduler {
    private static final String CHANNEL = "gentle_reminders";
    static void schedule(Context context, String id, String title, String slot) {
        int hour = TaskRepository.SLOT_MORNING.equals(slot) ? 9 : TaskRepository.SLOT_MIDDAY.equals(slot) ? 13 : TaskRepository.SLOT_EVENING.equals(slot) ? 18 : 20;
        LocalDateTime when = LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (when.isBefore(LocalDateTime.now())) when = when.plusDays(1);
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("task_id", id).putExtra("title", title).putExtra("slot", slot);
        PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending);
    }
    static void show(Context context, String title) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Sanfte Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT));
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(context, CHANNEL) : new android.app.Notification.Builder(context);
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(title.hashCode(), builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Kleiner Hinweis").setContentText(title).setContentIntent(pending).setAutoCancel(true).build());
    }
}
