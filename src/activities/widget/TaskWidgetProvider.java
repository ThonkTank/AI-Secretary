package activities.widget;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK WIDGET PROVIDER - AppWidgetProvider für Home-Screen Widget
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Verwaltet Widget-Lifecycle und verarbeitet Broadcast-Actions.
 *   Zentrale Anlaufstelle für Widget-Updates.
 *
 * DESIGN:
 *   - onUpdate(): Konfiguriert Widget (Adapter, PendingIntents)
 *   - onReceive(): Verarbeitet Toggle/Timer/Refresh Actions
 *   - notifyWidgetUpdate(): Statische Methode für externe Aufrufer
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * CUSTOM ACTIONS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   ACTION_TOGGLE = "com.autosecretary.widget.ACTION_TOGGLE"
 *     → Checkbox geklickt
 *     → Extras: slot_id (Long), was_completed (boolean)
 *     → Aktion: uncompleteSlot() wenn was_completed, sonst completeSlot()
 *
 *   ACTION_TIMER = "com.autosecretary.widget.ACTION_TIMER"
 *     → Timer-Button geklickt
 *     → Extras: slot_id (Long), timer_running (boolean)
 *     → Aktion: stopTimer() wenn timer_running, sonst startTimer()
 *
 *   ACTION_REFRESH = "com.autosecretary.widget.ACTION_REFRESH"
 *     → Manueller Refresh-Button (↻) geklickt
 *     → Aktion: notifyWidgetUpdate()
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * onUpdate(Context, AppWidgetManager, int[] appWidgetIds)
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   Für jeden appWidgetId:
 *     1. RemoteViews für widget_list.xml erstellen
 *     2. RemoteViewsAdapter setzen:
 *        Intent intent = new Intent(ctx, TaskWidgetService.class)
 *        rv.setRemoteAdapter(R.id.widget_list, intent)
 *     3. PendingIntent-Template für Liste setzen (FLAG_MUTABLE für Fill-In):
 *        Intent templateIntent = new Intent(ctx, TaskWidgetProvider.class)
 *        PendingIntent pendingTemplate = PendingIntent.getBroadcast(...)
 *        rv.setPendingIntentTemplate(R.id.widget_list, pendingTemplate)
 *     4. Header-Klick → App öffnen:
 *        Intent appIntent = new Intent(ctx, mainActivity.class)
 *        PendingIntent appPending = PendingIntent.getActivity(..., FLAG_IMMUTABLE)
 *        rv.setOnClickPendingIntent(R.id.widget_header, appPending)
 *     5. Refresh-Button → ACTION_REFRESH:
 *        Intent refreshIntent = new Intent(ctx, TaskWidgetProvider.class)
 *            .setAction(ACTION_REFRESH)
 *        PendingIntent refreshPending = PendingIntent.getBroadcast(..., FLAG_IMMUTABLE)
 *        rv.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
 *     6. appWidgetManager.updateAppWidget(appWidgetId, rv)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * onReceive(Context, Intent)
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   1. super.onReceive() aufrufen (für Standard-Actions)
 *   2. Action prüfen:
 *
 *      ACTION_TOGGLE:
 *        Long slotId = intent.getLongExtra("slot_id", -1)
 *        boolean wasCompleted = intent.getBooleanExtra("was_completed", false)
 *        todoManager manager = new todoManager(context)
 *        manager.provideList()  // WICHTIG: Initialisiert Manager!
 *        if (wasCompleted) manager.uncompleteSlot(slotId)
 *        else manager.completeSlot(slotId)
 *        notifyWidgetUpdate(context)
 *
 *      ACTION_TIMER:
 *        Long slotId = intent.getLongExtra("slot_id", -1)
 *        boolean timerRunning = intent.getBooleanExtra("timer_running", false)
 *        todoManager manager = new todoManager(context)
 *        manager.provideList()
 *        if (timerRunning) manager.stopTimer(slotId)
 *        else manager.startTimer(slotId)
 *        notifyWidgetUpdate(context)
 *
 *      ACTION_REFRESH:
 *        notifyWidgetUpdate(context)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * notifyWidgetUpdate(Context context) - STATIC
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   AppWidgetManager manager = AppWidgetManager.getInstance(context)
 *   ComponentName widget = new ComponentName(context, TaskWidgetProvider.class)
 *   int[] ids = manager.getAppWidgetIds(widget)
 *   if (ids != null && ids.length > 0) {
 *       manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
 *   }
 *
 *   Aufrufer:
 *     - DailyPlanningReceiver (nach Mitternachts-Planung)
 *     - onReceive() nach Checkbox-Toggle/Timer
 *     - WidgetRefreshApp (bei Geräte-Unlock)
 *
 */

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import com.autosecretary.R;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import activities.inApp.mainActivity;
import controller.todoManager;

public class TaskWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TOGGLE = "com.autosecretary.widget.ACTION_TOGGLE";
    private static final String ACTION_TIMER = "com.autosecretary.widget.ACTION_TIMER";
    private static final String ACTION_REFRESH = "com.autosecretary.widget.ACTION_REFRESH";
    private static final String ACTION_PREV_DAY = "com.autosecretary.widget.ACTION_PREV_DAY";
    private static final String ACTION_NEXT_DAY = "com.autosecretary.widget.ACTION_NEXT_DAY";
    public static final String ACTION_CREATE_ITEM = "com.autosecretary.widget.ACTION_CREATE_ITEM";

    // Flash-Feedback: Slot-ID die gerade mit Flash-Farbe angezeigt werden soll
    private static Long flashingSlotId = null;

    // SharedPreferences für persistenten Offset
    private static final String PREFS_NAME = "widget_prefs";
    private static final String KEY_OFFSET = "selected_day_offset";

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN);

    /** Gibt die Slot-ID zurück die gerade "flashen" soll (für TaskWidgetFactory) */
    public static Long getFlashingSlotId() {
        return flashingSlotId;
    }

    /** Lädt den Offset aus SharedPreferences */
    private static int getSelectedDayOffset(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET, 0);
    }

    /** Speichert den Offset in SharedPreferences */
    private static void setSelectedDayOffset(Context context, int offset) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OFFSET, offset)
            .commit();  // Synchron statt apply() - verhindert Race Condition mit onUpdate()
    }

    /** Gibt das aktuell ausgewählte Datum zurück (für TaskWidgetFactory) */
    public static LocalDate getSelectedDate(Context context) {
        return LocalDate.now().plusDays(getSelectedDayOffset(context));
    }

    /** Prüft ob der aktuell angezeigte Tag heute ist */
    public static boolean isShowingToday(Context context) {
        return getSelectedDayOffset(context) == 0;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_list);

            // 1. RemoteViewsAdapter für ListView setzen
            Intent serviceIntent = new Intent(context, TaskWidgetService.class);
            rv.setRemoteAdapter(R.id.widget_list, serviceIntent);
            rv.setEmptyView(R.id.widget_list, R.id.widget_empty);

            // 2. PendingIntent-Template für Liste (FLAG_MUTABLE für Fill-In Intents)
            Intent templateIntent = new Intent(context, TaskWidgetProvider.class);
            PendingIntent pendingTemplate = PendingIntent.getBroadcast(
                context, 0, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            rv.setPendingIntentTemplate(R.id.widget_list, pendingTemplate);

            // 3. Datum-Label aktualisieren
            int offset = getSelectedDayOffset(context);
            LocalDate selectedDate = getSelectedDate(context);
            String dateText = offset == 0 ? "Heute" : selectedDate.format(DATE_FORMAT);
            rv.setTextViewText(R.id.widget_date, dateText);

            // 4. Pfeil-Sichtbarkeit und -Farbe je nach Offset
            // Pfeil links: Nur aktiv wenn nicht heute
            if (offset == 0) {
                rv.setTextColor(R.id.widget_prev_day, 0x40FFFFFF); // 25% Alpha
            } else {
                rv.setTextColor(R.id.widget_prev_day, 0xFFFFFFFF); // Voll sichtbar
            }
            // Pfeil rechts: Nur aktiv wenn < 6 Tage
            if (offset >= 6) {
                rv.setTextColor(R.id.widget_next_day, 0x40FFFFFF);
            } else {
                rv.setTextColor(R.id.widget_next_day, 0xFFFFFFFF);
            }

            // 5. Datum-Klick → App öffnen
            Intent appIntent = new Intent(context, mainActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent appPending = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_date, appPending);

            // 6. Pfeil links → ACTION_PREV_DAY
            Intent prevIntent = new Intent(context, TaskWidgetProvider.class);
            prevIntent.setAction(ACTION_PREV_DAY);
            PendingIntent prevPending = PendingIntent.getBroadcast(
                context, 2, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_prev_day, prevPending);

            // 7. Pfeil rechts → ACTION_NEXT_DAY
            Intent nextIntent = new Intent(context, TaskWidgetProvider.class);
            nextIntent.setAction(ACTION_NEXT_DAY);
            PendingIntent nextPending = PendingIntent.getBroadcast(
                context, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_next_day, nextPending);

            // 8. Refresh-Button → ACTION_REFRESH
            Intent refreshIntent = new Intent(context, TaskWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPending = PendingIntent.getBroadcast(
                context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_refresh, refreshPending);

            // 9. Add-Button → mainActivity mit CREATE_ITEM Action starten
            Intent addIntent = new Intent(context, mainActivity.class);
            addIntent.setAction(ACTION_CREATE_ITEM);
            addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent addPending = PendingIntent.getActivity(
                context, 1, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.widget_add, addPending);

            // 10. Widget aktualisieren
            appWidgetManager.updateAppWidget(appWidgetId, rv);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) {
            // Fill-In Intent von Liste - Action aus Extra lesen
            action = intent.getStringExtra("action");
        }

        if ("toggle".equals(action)) {
            long slotId = intent.getLongExtra("slot_id", -1);
            boolean wasCompleted = intent.getBooleanExtra("was_completed", false);

            if (slotId != -1) {
                todoManager manager = new todoManager(context);
                manager.provideList(); // Initialisiert Manager

                if (wasCompleted) {
                    manager.uncompleteSlot(slotId);
                    notifyWidgetUpdate(context);
                } else {
                    manager.completeSlot(slotId);

                    // Flash-Feedback: Slot mit Flash-Farbe anzeigen
                    flashingSlotId = slotId;
                    notifyWidgetUpdate(context);

                    // Nach 300ms: Flash beenden und normal anzeigen
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        flashingSlotId = null;
                        notifyWidgetUpdate(context);
                    }, 300);
                }
            }
        }
        else if ("timer".equals(action)) {
            long slotId = intent.getLongExtra("slot_id", -1);
            boolean timerRunning = intent.getBooleanExtra("timer_running", false);

            if (slotId != -1) {
                todoManager manager = new todoManager(context);
                manager.provideList();

                if (timerRunning) {
                    manager.stopTimer(slotId);
                } else {
                    manager.startTimer(slotId);
                }

                notifyWidgetUpdate(context);
            }
        }
        else if ("increment_progress".equals(action)) {
            long slotId = intent.getLongExtra("slot_id", -1);
            boolean alreadyDoneToday = intent.getBooleanExtra("already_done_today", false);

            if (slotId != -1) {
                todoManager manager = new todoManager(context);
                manager.provideList();
                manager.incrementProgress(slotId);

                // Flash-Feedback nur wenn noch nicht "heute erledigt"
                if (!alreadyDoneToday) {
                    flashingSlotId = slotId;
                    notifyWidgetUpdate(context);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        flashingSlotId = null;
                        notifyWidgetUpdate(context);
                    }, 300);
                } else {
                    notifyWidgetUpdate(context);
                }
            }
        }
        else if ("decrement_progress".equals(action)) {
            long slotId = intent.getLongExtra("slot_id", -1);

            if (slotId != -1) {
                todoManager manager = new todoManager(context);
                manager.provideList();
                manager.decrementProgress(slotId);
                notifyWidgetUpdate(context);
            }
        }
        else if ("show_description".equals(action)) {
            String title = intent.getStringExtra("task_title");
            String description = intent.getStringExtra("task_description");

            Intent popupIntent = new Intent(context, DescriptionPopupActivity.class);
            popupIntent.putExtra(DescriptionPopupActivity.EXTRA_TITLE, title);
            popupIntent.putExtra(DescriptionPopupActivity.EXTRA_DESCRIPTION, description);
            popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(popupIntent);
        }
        else if (ACTION_REFRESH.equals(action)) {
            notifyWidgetUpdate(context);
        }
        else if (ACTION_PREV_DAY.equals(action)) {
            int offset = getSelectedDayOffset(context);
            if (offset > 0) {
                setSelectedDayOffset(context, offset - 1);
                updateWidgetFully(context);
            }
        }
        else if (ACTION_NEXT_DAY.equals(action)) {
            int offset = getSelectedDayOffset(context);
            if (offset < 6) {
                setSelectedDayOffset(context, offset + 1);
                updateWidgetFully(context);
            }
        }
    }

    /**
     * Vollständiges Widget-Update (Header + Liste).
     * Notwendig bei Tag-Wechsel, da sich Header-Text und Pfeil-Farben ändern.
     */
    private void updateWidgetFully(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, TaskWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);

        if (ids != null && ids.length > 0) {
            // Header aktualisieren (onUpdate neu aufrufen)
            onUpdate(context, manager, ids);
            // Liste aktualisieren
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        }
    }

    /**
     * Aktualisiert alle Widget-Instanzen.
     * Kann von externen Komponenten aufgerufen werden (z.B. DailyPlanningReceiver).
     */
    public static void notifyWidgetUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, TaskWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);

        if (ids != null && ids.length > 0) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        }
    }
}
