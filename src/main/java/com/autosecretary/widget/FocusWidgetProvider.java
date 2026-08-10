package com.autosecretary.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.MainActivity;

/** Homescreen behavior anchor: complete the whole block or explicitly move it behind today. */
public final class FocusWidgetProvider extends AppWidgetProvider {
    static final String ACTION_COMPLETE = "com.autosecretary.COMPLETE";
    static final String ACTION_LATER = "com.autosecretary.LATER";
    static final String EXTRA_ID = "obligation_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) update(context, manager, widgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String id = intent.getStringExtra(EXTRA_ID);
        if (id == null) return;
        PendingResult result = goAsync();
        AutoSecretaryApplication app = AutoSecretaryApplication.from(context);
        if (ACTION_COMPLETE.equals(intent.getAction())) {
            app.repository().complete(id, ignored -> result.finish());
        } else if (ACTION_LATER.equals(intent.getAction())) {
            app.repository().postpone(id, result::finish);
        } else {
            result.finish();
        }
    }

    private void update(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_focus);
        Intent service = new Intent(context, FocusWidgetService.class);
        service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        service.setData(Uri.parse("widget://focus/" + widgetId + "/" + System.currentTimeMillis()));
        views.setRemoteAdapter(R.id.WidgetList, service);
        views.setEmptyView(R.id.WidgetList, R.id.WidgetEmpty);

        Intent template = new Intent(context, FocusWidgetProvider.class);
        template.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent templatePending = PendingIntent.getBroadcast(
                context, widgetId, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.WidgetList, templatePending);

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.WidgetHeader, openPending);
        views.setOnClickPendingIntent(R.id.WidgetAdd, openPending);
        manager.updateAppWidget(widgetId, views);
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.WidgetList);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, FocusWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) return;
        Intent update = new Intent(context, FocusWidgetProvider.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(update);
    }
}
