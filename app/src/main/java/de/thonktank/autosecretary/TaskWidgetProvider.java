package de.thonktank.autosecretary;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.List;

/** The widget intentionally offers action, not editing. */
public class TaskWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TaskWidgetProvider.class));
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    private static RemoteViews build(Context context) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.task_widget);
        TaskRepository store = new TaskRepository(context);
        List<JSONObject> tasks = store.activeTasks();
        view.setOnClickPendingIntent(R.id.widget_title, openApp(context));
        if (tasks.isEmpty()) {
            view.setTextViewText(R.id.widget_title, "Auto Secretary");
            view.setTextViewText(R.id.widget_subtitle, "Heute ist nichts offen.");
            view.setTextViewText(R.id.widget_step, "Eine neue Aufgabe anlegen");
            view.setViewVisibility(R.id.widget_later, View.GONE);
            view.setViewVisibility(R.id.widget_done, View.GONE);
            view.setViewVisibility(R.id.widget_condition, View.GONE);
            return view;
        }
        JSONObject task = tasks.get(0);
        String id = task.optString("id");
        view.setTextViewText(R.id.widget_title, task.optString("title"));
        view.setTextViewText(R.id.widget_subtitle, task.optString("slot") + " · " + store.remainingSteps(task) + " Schritte offen");
        view.setTextViewText(R.id.widget_step, store.nextAction(task));
        view.setViewVisibility(R.id.widget_done, View.VISIBLE);
        view.setViewVisibility(R.id.widget_later, View.VISIBLE);
        boolean condition = task.optBoolean("ongoing") && !task.optString("condition").isEmpty();
        boolean conditionReady = condition && store.remainingSteps(task) == 0;
        view.setTextViewText(R.id.widget_done, conditionReady ? "Bedingung erfüllt" : "Erledigt");
        view.setOnClickPendingIntent(R.id.widget_done, action(context, conditionReady ? TaskActionReceiver.CONDITION : TaskActionReceiver.COMPLETE, id));
        view.setOnClickPendingIntent(R.id.widget_later, action(context, TaskActionReceiver.LATER, id));
        view.setViewVisibility(R.id.widget_condition, condition && !conditionReady ? View.VISIBLE : View.GONE);
        if (condition && !conditionReady) view.setOnClickPendingIntent(R.id.widget_condition, action(context, TaskActionReceiver.CONDITION, id));
        return view;
    }

    private static PendingIntent action(Context context, String action, String id) {
        Intent intent = new Intent(context, TaskActionReceiver.class).setAction(action).putExtra("task_id", id);
        return PendingIntent.getBroadcast(context, (action + id).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
