package scheduling;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import entities.TodoList;
import entities.TodoList.TimeSlot;
import entities.TrackedItem;
import repository.SQLrepo;
import repository.Table;

/**
 * Tagesabschluss-Logik: Gestrige ToDo-Liste auswerten und aufräumen.
 * Wird täglich um 00:00 getriggert (vor buildToDo).
 *
 * 1. Gestrige Slots auswerten → item.update() mit Slot-Daten aufrufen
 * 2. ALLE übrigen Items refreshen → item.update(null,...) für "immer"-Updates
 * 3. Alte todoLists aus DB entfernen
 */
public class CleanToDo {

    /**
     * Gestrige Liste auswerten, alle Items refreshen, vergangene Listen entfernen.
     */
    public static void clean(SQLrepo repo) {
        LocalDate today = LocalDate.now();
        LocalDate gestern = today.minusDays(1);

        // 1. Gestrige Slots auswerten (update mit Slot-Daten)
        Set<Long> updatedItemIds = new HashSet<>();
        TodoList liste = repo.fetch(Table.TODOS, Map.of("date", gestern.toString()));
        if (liste != null && liste.timeSlots != null) {
            processSlots(liste.timeSlots, gestern, repo, updatedItemIds);
        }

        // 2. ALLE übrigen Items refreshen (update ohne Slot-Daten)
        refreshRemainingItems(repo, updatedItemIds);

        // 3. Alte todoLists entfernen
        cleanOrphanedLists(repo, today);
    }

    /**
     * Entfernt alle todoLists deren Datum in der Vergangenheit liegt.
     */
    private static void cleanOrphanedLists(SQLrepo repo, LocalDate today) {
        SQLiteDatabase db = repo.getWritableDatabase();
        String todayStr = today.toString();

        // Erst time_slots der alten Listen löschen (FK-Constraint)
        Cursor cursor = db.rawQuery(
            "SELECT id FROM todos WHERE date < ?", new String[]{todayStr});
        List<Long> oldIds = new ArrayList<>();
        while (cursor.moveToNext()) {
            oldIds.add(cursor.getLong(0));
        }
        cursor.close();

        for (Long id : oldIds) {
            db.delete("time_slots", "todo_id = ?", new String[]{String.valueOf(id)});
        }

        // Dann die todos selbst löschen
        db.delete("todos", "date < ?", new String[]{todayStr});
    }

    /**
     * Ruft item.update(null,...) für alle Items auf, die nicht bereits
     * durch processSlots() aktualisiert wurden.
     * Damit bekommen auch nicht-geschedulte Items die "immer"-Updates:
     * Perioden-Reset, scheduled-Bereinigung, blockedDays-Refresh.
     */
    private static void refreshRemainingItems(SQLrepo repo, Set<Long> alreadyUpdated) {
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(repo.lookups("items", Map.of("is_completed", "0"), "id"));
        allIds.addAll(repo.lookups("items", Map.of("is_completed", "1"), "id"));

        LocalDate today = LocalDate.now();
        for (Long id : allIds) {
            if (alreadyUpdated.contains(id)) continue;
            TrackedItem item = repo.fetch(Table.ITEMS, id);
            if (item == null) continue;
            item.update(null, null, null, null, null, today, repo);
        }
    }

    /**
     * Rekursiv alle Slots durchgehen und trackedItem.update() aufrufen.
     * previousCompletedItemId kommt jetzt aus dem Slot selbst (von TodoManager gesetzt),
     * nicht mehr aus der Iterations-Reihenfolge.
     */
    private static void processSlots(List<TimeSlot> slots, LocalDate day,
                                      SQLrepo repo, Set<Long> updatedItemIds) {
        for (TimeSlot slot : slots) {
            // Item updaten wenn vorhanden
            if (slot.item != null) {
                TrackedItem item = repo.fetch(Table.ITEMS, slot.item);
                if (item != null) {
                    boolean completed = slot.completed != null && slot.completed;
                    // previousItemId aus Slot lesen (tatsächliche Completion-Reihenfolge)
                    Long prevId = completed ? slot.previousCompletedItemId : null;

                    item.update(completed, slot.workStart, slot.workEnd, prevId, slot.progressDelta, day, repo);
                    updatedItemIds.add(slot.item);
                }
            }

            // Verschachtelte Slots (Tasks innerhalb Goal-Slots)
            if (slot.timeSlots != null) {
                processSlots(slot.timeSlots, day, repo, updatedItemIds);
            }
        }
    }
}
