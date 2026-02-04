package activities.widget;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK WIDGET FACTORY - RemoteViewsFactory für Task-Liste
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Liefert RemoteViews für jeden Listen-Eintrag im Widget.
 *   Verwendet dieselben Layouts und Renderer wie die App.
 *
 * DESIGN:
 *   - Lädt Daten in onDataSetChanged() via todoManager.provideList()
 *   - getViewAt() delegiert an TaskRowRenderer
 *   - Fill-In Intents für Checkbox und Timer (setOnClickFillInIntent)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * LIFECYCLE
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   onCreate()
 *     → Initialisierung (KEINE Daten laden hier!)
 *     → Package-Name merken für RemoteViews-Constructor
 *
 *   onDataSetChanged()
 *     → todoManager instanzieren
 *     → manager.provideList() aufrufen
 *     → TaskListData.fromEntries() → List<DisplayRow>
 *     → In Member-Variable speichern
 *
 *   getCount()
 *     → rows.size()
 *
 *   getViewAt(int position)
 *     → DisplayRow row = rows.get(position)
 *     → Pattern-Matching: GoalHeader / TaskItem / CalendarEvent
 *     → new RemoteViews(pkg, R.layout.item_*)
 *     → TaskRowRenderer.apply*(rv, config, ctx)
 *     → setOnClickFillInIntent() für interaktive Elemente
 *     → return rv
 *
 *   getViewTypeCount()
 *     → 3 (GoalHeader, TaskItem, CalendarEvent)
 *
 *   getItemId(int position)
 *     → position (oder slotId für Tasks)
 *
 *   hasStableIds()
 *     → false (Slots können sich zwischen Updates ändern)
 *
 *   onDestroy()
 *     → Cleanup (rows = null)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * FILL-IN INTENTS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   Für Checkbox (task_checkbox):
 *     Intent intent = new Intent()
 *         .putExtra("action", "toggle")
 *         .putExtra("slot_id", cfg.slotId())
 *         .putExtra("was_completed", cfg.checked());
 *     rv.setOnClickFillInIntent(R.id.task_checkbox, intent);
 *
 *   Für Timer (task_timer):
 *     Intent intent = new Intent()
 *         .putExtra("action", "timer")
 *         .putExtra("slot_id", cfg.slotId())
 *         .putExtra("timer_running", cfg.timerRunning());
 *     rv.setOnClickFillInIntent(R.id.task_timer, intent);
 *
 */

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import controller.todoManager;
import data.TaskListData;
import data.TaskListData.*;
import data.TaskRowConfig.*;
import render.TaskRowRenderer;

import java.util.ArrayList;
import java.util.List;

public class TaskWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

    private final Context context;
    private final String packageName;
    private List<DisplayRow> rows = new ArrayList<>();

    public TaskWidgetFactory(Context context) {
        this.context = context;
        this.packageName = context.getPackageName();
    }

    @Override
    public void onCreate() {
        // Keine Daten hier laden - wird in onDataSetChanged() gemacht
    }

    @Override
    public void onDataSetChanged() {
        // Daten neu laden
        todoManager manager = new todoManager(context);
        List<todoManager.TaskEntry> entries = manager.provideList();
        rows = TaskListData.fromEntries(entries);
    }

    @Override
    public void onDestroy() {
        rows = null;
    }

    @Override
    public int getCount() {
        return rows != null ? rows.size() : 0;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (rows == null || position >= rows.size()) {
            return null;
        }

        DisplayRow row = rows.get(position);

        if (row instanceof GoalHeader header) {
            RemoteViews rv = new RemoteViews(packageName, R.layout.item_goal_header);
            GoalHeaderConfig cfg = GoalHeaderConfig.from(header);
            TaskRowRenderer.applyGoalHeader(rv, cfg, context);
            return rv;
        }
        else if (row instanceof TaskItem item) {
            RemoteViews rv = new RemoteViews(packageName, R.layout.item_task);
            TaskConfig cfg = TaskConfig.from(item.entry());
            TaskRowRenderer.applyTask(rv, cfg, context);

            // Flash-Feedback: Hintergrund überschreiben wenn dieser Slot gerade flasht
            Long flashingId = TaskWidgetProvider.getFlashingSlotId();
            if (flashingId != null && flashingId.equals(cfg.slotId())) {
                rv.setInt(R.id.task_row, "setBackgroundColor",
                    ContextCompat.getColor(context, R.color.completion_flash));
            }

            // Fill-In Intents je nach Modus
            if (cfg.hasProgress()) {
                // Progress-Modus: +/- Buttons
                Intent plusIntent = new Intent()
                    .putExtra("action", "increment_progress")
                    .putExtra("slot_id", cfg.slotId())
                    .putExtra("already_done_today", cfg.progressDoneToday());
                rv.setOnClickFillInIntent(R.id.btn_progress_plus, plusIntent);

                Intent minusIntent = new Intent()
                    .putExtra("action", "decrement_progress")
                    .putExtra("slot_id", cfg.slotId());
                rv.setOnClickFillInIntent(R.id.btn_progress_minus, minusIntent);
            } else {
                // Normal-Modus: Checkbox
                Intent checkIntent = new Intent()
                    .putExtra("action", "toggle")
                    .putExtra("slot_id", cfg.slotId())
                    .putExtra("was_completed", cfg.checked());
                rv.setOnClickFillInIntent(R.id.task_checkbox, checkIntent);
            }

            // Fill-In Intent für Timer (nur wenn sichtbar)
            if (cfg.showTimer()) {
                Intent timerIntent = new Intent()
                    .putExtra("action", "timer")
                    .putExtra("slot_id", cfg.slotId())
                    .putExtra("timer_running", cfg.timerRunning());
                rv.setOnClickFillInIntent(R.id.task_timer, timerIntent);
            }

            return rv;
        }
        else if (row instanceof CalendarEvent event) {
            RemoteViews rv = new RemoteViews(packageName, R.layout.item_calendar);
            CalendarConfig cfg = CalendarConfig.from(event.entry());
            TaskRowRenderer.applyCalendar(rv, cfg, context);
            return rv;
        }

        return null;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null; // Default loading view
    }

    @Override
    public int getViewTypeCount() {
        return 3; // GoalHeader, TaskItem, CalendarEvent
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return false; // Slots können sich zwischen Updates ändern
    }
}
