package com.autosecretary.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.autosecretary.presentation.R;
import com.autosecretary.domain.SolarDaylight;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;


/** Homescreen behavior anchor: complete a block or step and move blocks behind today. */
public final class FocusWidgetProvider extends AppWidgetProvider {
    static final String ACTION_COMPLETE = "com.autosecretary.COMPLETE";
    static final String ACTION_LATER = "com.autosecretary.LATER";
    static final String ACTION_TOGGLE_STEP = "com.autosecretary.TOGGLE_STEP";
    static final String ACTION_UNDO = "com.autosecretary.UNDO";
    static final String EXTRA_ID = "obligation_id";
    static final String EXTRA_STEP_ID = "step_id";
    static final String EXTRA_STEP_COMPLETED = "step_completed";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) update(context, manager, widgetId);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            Bundle newOptions) {
        update(context, manager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
            refreshAll(context);
            return;
        }
        String id = intent.getStringExtra(EXTRA_ID);
        boolean undo = ACTION_UNDO.equals(intent.getAction());
        if (id == null && !undo) return;
        PendingResult result = goAsync();
        WidgetDependencies dependencies = WidgetDependencies.from(context);
        dependencies.executeDatabase(() -> {
            try {
                LocalDateTime now = LocalDateTime.ofInstant(
                        dependencies.time().now(), dependencies.time().zone());
                if (undo) {
                    dependencies.workItems().undoLatest(now);
                } else if (ACTION_COMPLETE.equals(intent.getAction())) {
                    dependencies.workItems().complete(id, now);
                } else if (ACTION_LATER.equals(intent.getAction())) {
                    dependencies.moveWorkItem().omitToday(id);
                } else if (ACTION_TOGGLE_STEP.equals(intent.getAction())) {
                    String stepId = intent.getStringExtra(EXTRA_STEP_ID);
                    if (stepId == null) return;
                    boolean completed = intent.getBooleanExtra(EXTRA_STEP_COMPLETED, true);
                    dependencies.workItems().setStepCompleted(
                            id, stepId, completed, now);
                }
                dependencies.refreshWidgets();
            } finally {
                result.finish();
            }
        });
    }

    private void update(Context context, AppWidgetManager manager, int widgetId) {
        Bundle options = manager.getAppWidgetOptions(widgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 384);
        boolean shortWidget = minHeight < 240;
        boolean wideWidget = shortWidget && minWidth >= 260;
        int layout = shortWidget
                ? minWidth < 260 ? R.layout.widget_focus_compact : R.layout.widget_focus_wide
                : minWidth < 340 ? R.layout.widget_focus_tall : R.layout.widget_focus;
        int maxRows = shortWidget ? 1 : minWidth < 340 ? 2 : 3;
        boolean showSteps = !(shortWidget && minWidth < 260);
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        WidgetScene scene = widgetScene(context);
        views.setImageViewResource(R.id.WidgetDaylight, scene.daylight());
        views.setImageViewResource(R.id.WidgetForest, switch (scene.palette()) {
            case 1 -> R.drawable.widget_forest_dark;
            case 2 -> R.drawable.widget_forest_evening;
            default -> R.drawable.widget_forest;
        });
        views.setInt(R.id.WidgetRoot, "setBackgroundResource", switch (scene.palette()) {
            case 1 -> R.drawable.bg_widget_dark;
            case 2 -> R.drawable.bg_widget_evening;
            default -> R.drawable.bg_widget;
        });
        var clock = WidgetDependencies.from(context).time();
        views.setTextViewText(R.id.WidgetGreeting, shortWidget ? "jetzt" : greeting(
                LocalDateTime.ofInstant(clock.now(), clock.zone()).toLocalTime()));
        views.setTextColor(R.id.WidgetGreeting, scene.palette() == 2 ? 0xFFBCAB8C
                : scene.palette() == 1 ? 0xFFA9B9AC : 0xFF6D7860);
        views.setTextColor(R.id.WidgetAdd, scene.palette() == 2 ? 0xFFF0A03C
                : scene.palette() == 1 ? 0xFFE8A83E : 0xFF2E6B44);
        views.setTextColor(R.id.WidgetEmpty, scene.palette() == 2 ? 0xFFF8ECD2
                : scene.palette() == 1 ? 0xFFF4EEDA : 0xFF1A2618);
        views.setTextViewText(R.id.WidgetEmpty, "Heute darf ein neues Blatt wachsen.");
        Intent service = new Intent(context, FocusWidgetService.class);
        service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        service.putExtra(FocusWidgetService.EXTRA_MAX_ROWS, maxRows);
        service.putExtra(FocusWidgetService.EXTRA_SHOW_STEPS, showSteps);
        service.putExtra(FocusWidgetService.EXTRA_WIDE, wideWidget);
        service.putExtra(FocusWidgetService.EXTRA_PALETTE, scene.palette());
        service.setData(Uri.parse("widget://focus/" + widgetId + "/"
                + clock.now().toEpochMilli()));
        views.setRemoteAdapter(R.id.WidgetList, service);
        views.setEmptyView(R.id.WidgetList, R.id.WidgetEmpty);

        Intent template = new Intent(context, FocusWidgetProvider.class);
        template.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent templatePending = PendingIntent.getBroadcast(
                context, widgetId, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.WidgetList, templatePending);

        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open == null) open = new Intent();
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.WidgetHeader, openPending);
        views.setOnClickPendingIntent(R.id.WidgetAdd, openPending);
        views.setOnClickPendingIntent(R.id.WidgetEmpty, openPending);
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

    private static WidgetScene widgetScene(Context context) {
        WidgetDependencies dependencies = WidgetDependencies.from(context);
        var time = dependencies.time();
        double latitude = 51.20;
        double longitude = 6.69;
        try {
            var position = dependencies.location().lastKnown();
            if (position != null) {
                latitude = position.latitude();
                longitude = position.longitude();
            }
        } catch (RuntimeException ignored) { }
        LocalDateTime now = LocalDateTime.ofInstant(time.now(), time.zone());
        var window = SolarDaylight.forDate(
                now.toLocalDate(), latitude, longitude, time.zone());
        int sunrise = window.sunrise().getHour() * 60 + window.sunrise().getMinute();
        int sunset = window.sunset().getHour() * 60 + window.sunset().getMinute();
        int daylight = Math.max(1, sunset - sunrise);
        int[] marks = {sunrise - 130, sunrise,
                sunrise + Math.round(daylight * .242f),
                sunrise + Math.round(daylight * .503f),
                sunrise + Math.round(daylight * .815f),
                sunset, sunset + 125, sunset + 255};
        int minute = now.getHour() * 60 + now.getMinute();
        int index = 7;
        for (int candidate = 0; candidate < marks.length; candidate++) {
            if (minute <= marks[candidate]) {
                index = candidate;
                break;
            }
        }
        int daylightDrawable = new int[] {R.drawable.widget_daylight_0, R.drawable.widget_daylight_1,
                R.drawable.widget_daylight_2, R.drawable.widget_daylight_3,
                R.drawable.widget_daylight_4, R.drawable.widget_daylight_5,
                R.drawable.widget_daylight_6, R.drawable.widget_daylight_7}[index];
        int palette = index >= 2 && index <= 4 ? 0 : index == 5 ? 2 : 1;
        return new WidgetScene(daylightDrawable, palette);
    }

    private record WidgetScene(int daylight, int palette) { }

    private static String greeting(java.time.LocalTime time) {
        int minute = time.getHour() * 60 + time.getMinute();
        return minute < 5 * 60 ? "Noch früh"
                : minute < 9 * 60 ? "Guten Morgen"
                : minute < 12 * 60 ? "Vormittag"
                : minute < 14 * 60 ? "Mittag"
                : minute < 18 * 60 ? "Nachmittag"
                : minute < 21 * 60 ? "Guten Abend"
                : minute < 23 * 60 ? "Es wird spät" : "Gute Nacht";
    }
}
