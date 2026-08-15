package de.thonktank.autosecretary;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A widget is a second view of the same task service, never a separate data path. */
public class TaskWidgetProvider extends AppWidgetProvider {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        PendingResult pending = goAsync(); Context app = context.getApplicationContext();
        WORKER.execute(() -> { try { for (int id : ids) manager.updateAppWidget(id, build(app)); } finally { pending.finish(); } });
    }
    static void updateAll(Context context) {
        Context app = context.getApplicationContext(); WORKER.execute(() -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(app); int[] ids = manager.getAppWidgetIds(new ComponentName(app, TaskWidgetProvider.class));
            for (int id : ids) manager.updateAppWidget(id, build(app));
        });
    }
    private static RemoteViews build(Context context) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.task_widget); DashboardState state = new TaskService(DatabaseProvider.get(context)).dashboard();
        view.setOnClickPendingIntent(R.id.widget_title, openApp(context));
        if (state.tasks.isEmpty()) { view.setTextViewText(R.id.widget_title, "Auto Secretary"); view.setTextViewText(R.id.widget_subtitle, "Heute ist nichts offen."); view.setTextViewText(R.id.widget_step, "Eine neue Aufgabe anlegen"); view.setViewVisibility(R.id.widget_later, View.GONE); view.setViewVisibility(R.id.widget_done, View.GONE); view.setViewVisibility(R.id.widget_condition, View.GONE); return view; }
        TaskSnapshot task = state.tasks.get(0); view.setTextViewText(R.id.widget_title, task.title); view.setTextViewText(R.id.widget_subtitle, task.slot + " · " + task.remainingSteps + " Schritte offen"); view.setTextViewText(R.id.widget_step, task.nextAction);
        boolean virtualCondition = task.occurrenceId.isEmpty(); view.setViewVisibility(R.id.widget_done, virtualCondition ? View.GONE : View.VISIBLE); view.setViewVisibility(R.id.widget_later, virtualCondition ? View.GONE : View.VISIBLE);
        if (!virtualCondition) { view.setOnClickPendingIntent(R.id.widget_done, action(context, TaskActionReceiver.COMPLETE, task.occurrenceId)); view.setOnClickPendingIntent(R.id.widget_later, action(context, TaskActionReceiver.LATER, task.occurrenceId)); }
        view.setViewVisibility(R.id.widget_condition, task.terminalCondition ? View.VISIBLE : View.GONE); if (task.terminalCondition) view.setOnClickPendingIntent(R.id.widget_condition, confirm(context, task.taskId));
        return view;
    }
    private static PendingIntent action(Context context, String action, String occurrenceId) { Intent intent = new Intent(context, TaskActionReceiver.class).setAction(action).putExtra("occurrence_id", occurrenceId); return PendingIntent.getBroadcast(context, (action + occurrenceId).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
    private static PendingIntent confirm(Context context, String taskId) { Intent intent = new Intent(context, MainActivity.class).putExtra(MainActivity.CONFIRM_TASK, taskId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); return PendingIntent.getActivity(context, ("confirm" + taskId).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
    private static PendingIntent openApp(Context context) { return PendingIntent.getActivity(context, 1, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
}
