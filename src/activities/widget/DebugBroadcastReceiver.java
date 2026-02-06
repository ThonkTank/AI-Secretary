package activities.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import controller.WidgetUpdateManager;
import controller.WidgetUpdateManager.DataDomain;
import entities.TodoList;
import entities.TodoList.TimeSlot;
import entities.TrackedItem;
import repository.SQLrepo;
import repository.Table;
import scheduling.BuildToDo;
import scheduling.CalendarReader;

/**
 * Debug-Receiver fuer ADB-getriggerte Aktionen.
 * Wird exportiert (exported=true) um ADB-Broadcasts zu empfangen.
 *
 * AKTIONEN:
 *   DUMP_PLANS - Gibt alle 7 Tagespläne via Logcat aus
 *   REPLAN_ALL - Löscht alle Pläne und generiert neu
 *
 * ADB-BEFEHLE:
 *   adb shell am broadcast -a com.autosecretary.debug.DUMP_PLANS
 *   adb shell am broadcast -a com.autosecretary.debug.REPLAN_ALL
 *
 * LOGCAT-FILTER:
 *   adb logcat | grep DebugPlan
 */
public class DebugBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "DebugPlan";

    public static final String ACTION_DUMP_PLANS = "com.autosecretary.debug.DUMP_PLANS";
    public static final String ACTION_REPLAN_ALL = "com.autosecretary.debug.REPLAN_ALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SQLrepo repo = SQLrepo.getInstance(context);

        switch (action) {
            case ACTION_DUMP_PLANS:
                dumpPlans(repo);
                break;

            case ACTION_REPLAN_ALL:
                replanAll(context, repo);
                break;

            default:
                Log.w(TAG, "Unbekannte Action: " + action);
        }
    }

    // ========================================================================
    // DUMP_PLANS: 7-Tage Plan formatiert ausgeben (wie TestBuildToDo)
    // ========================================================================
    private void dumpPlans(SQLrepo repo) {
        LocalDate today = LocalDate.now();
        int totalSlots = 0;
        int daysWithPlans = 0;

        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            String dateStr = day.toString();
            String dayName = day.getDayOfWeek().toString();

            TodoList list = repo.fetch(Table.TODOS, Map.of("date", dateStr));

            if (list == null) {
                Log.i(TAG, String.format("\n=== %s %s (kein Plan) ===", dayName, dateStr));
                continue;
            }

            daysWithPlans++;
            Log.i(TAG, String.format("\n=== %s %s (%s - %s) ===",
                dayName, dateStr, list.start, list.end));

            if (list.timeSlots == null || list.timeSlots.isEmpty()) {
                Log.i(TAG, "  (keine Eintraege)");
                continue;
            }

            for (TimeSlot slot : list.timeSlots) {
                totalSlots++;
                printSlot(repo, slot, "  ");
            }
        }

        Log.i(TAG, String.format("\n=== Zusammenfassung: %d Tage, %d Goal-Slots ===",
            daysWithPlans, totalSlots));
    }

    private void printSlot(SQLrepo repo, TimeSlot slot, String indent) {
        // Kalender-Event
        if (slot.isCalendarEvent != null && slot.isCalendarEvent) {
            Log.i(TAG, String.format("%s%s-%s  [KALENDER] %s",
                indent, slot.start, slot.end, slot.calendarTitle));
            return;
        }

        // Item-Infos laden
        String title = "???";
        String type = "";
        String prio = "";
        String budgetInfo = "";
        String completedMark = "";

        if (slot.item != null) {
            TrackedItem item = repo.fetch(Table.ITEMS, slot.item);
            if (item != null) {
                title = item.title;
                type = item.type.name();
                prio = item.priority.name();
                if (item.budgetRequirementCents > 0) {
                    budgetInfo = String.format(" [Budget: %.2f€]", item.budgetRequirementCents / 100.0);
                }
            }
        }

        if (slot.completed != null && slot.completed) {
            completedMark = " ✓";
        }

        String adjPrio = slot.adjustedPrio != null ? ", score=" + slot.adjustedPrio : "";
        Log.i(TAG, String.format("%s%s-%s  %s [%s, %s%s]%s%s",
            indent, slot.start, slot.end, title, type, prio, adjPrio, budgetInfo, completedMark));

        // Verschachtelte Task-Slots
        if (slot.timeSlots != null) {
            for (TimeSlot child : slot.timeSlots) {
                printSlot(repo, child, indent + "    ");
            }
        }
    }

    // ========================================================================
    // REPLAN_ALL: Alle Pläne löschen und neu generieren
    // ========================================================================
    private void replanAll(Context context, SQLrepo repo) {
        Log.i(TAG, "=== REPLAN_ALL gestartet ===");
        LocalDate today = LocalDate.now();

        // 1. Alle bestehenden Pläne löschen (heute + Zukunft)
        Log.i(TAG, "Schritt 1: Lösche bestehende Pläne...");
        int deletedCount = clearAllPlans(repo, today);
        Log.i(TAG, "  " + deletedCount + " todoLists gelöscht");

        // 2. Scheduled-Daten der Items im 7-Tage-Fenster bereinigen
        Log.i(TAG, "Schritt 2: Entplane Items...");
        int unscheduledCount = unscheduleAllItems(repo, today);
        Log.i(TAG, "  " + unscheduledCount + " Items aktualisiert");

        // 3. Neue Pläne generieren via BuildToDo.planWeek()
        Log.i(TAG, "Schritt 3: Generiere neue Pläne...");
        BuildToDo planner = new BuildToDo(repo,
            (day, start, end) -> CalendarReader.getEventsForDay(context, day, start, end)
        );
        List<TodoList> newLists = planner.planWeek();
        Log.i(TAG, "  " + newLists.size() + " neue todoLists erstellt");

        // 4. Widget aktualisieren (via decoupled Manager)
        WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO);
        Log.i(TAG, "Widget aktualisiert");

        Log.i(TAG, "=== REPLAN_ALL abgeschlossen ===\n");

        // 5. Neuen Plan ausgeben
        dumpPlans(repo);
    }

    /**
     * Löscht alle todoLists ab fromDate (inkl.) und deren time_slots.
     */
    private int clearAllPlans(SQLrepo repo, LocalDate fromDate) {
        SQLiteDatabase db = repo.getWritableDatabase();
        String fromDateStr = fromDate.toString();

        // Erst IDs der betroffenen todos sammeln
        List<Long> todoIds = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id FROM todos WHERE date >= ?", new String[]{fromDateStr})) {
            while (cursor.moveToNext()) {
                todoIds.add(cursor.getLong(0));
            }
        }

        // time_slots der betroffenen todos löschen (FK-Constraint)
        for (Long id : todoIds) {
            db.delete("time_slots", "todo_id = ?", new String[]{String.valueOf(id)});
        }

        // Dann todos selbst löschen
        return db.delete("todos", "date >= ?", new String[]{fromDateStr});
    }

    /**
     * Entfernt scheduled-Daten im 7-Tage-Fenster und berechnet blockedDays neu.
     */
    private int unscheduleAllItems(SQLrepo repo, LocalDate today) {
        List<Long> allIds = repo.lookups("items", Map.of(), "id");
        int count = 0;

        for (Long id : allIds) {
            TrackedItem item = repo.fetch(Table.ITEMS, id);
            if (item == null) continue;

            boolean modified = false;

            // Scheduled-Daten im 7-Tage-Fenster entfernen
            if (item.scheduled != null && !item.scheduled.isEmpty()) {
                List<LocalDate> toRemove = new ArrayList<>();
                for (LocalDate d : item.scheduled) {
                    if (!d.isBefore(today) && !d.isAfter(today.plusDays(6))) {
                        toRemove.add(d);
                    }
                }
                if (!toRemove.isEmpty()) {
                    item.scheduled.removeAll(toRemove);
                    modified = true;
                }
            }

            // blockedDays neu berechnen
            if (modified) {
                item.blockedDays = item.getBlockedDays();
                repo.write(item);
                count++;
            }
        }
        return count;
    }
}
