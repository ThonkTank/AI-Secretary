package scheduling;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import entities.todoList;
import entities.todoList.TimeSlot;
import entities.trackedItem;
import repository.SQLrepo;
import repository.Table;

/**
 * Tagesabschluss-Logik: Gestrige ToDo-Liste auswerten und aufräumen.
 * Wird täglich um 00:00 getriggert (vor buildToDo).
 * 
 * TODO: Alle Items Updaten.
 */
public class cleanToDo {

    /**
     * Gestrige Liste auswerten, alle vergangenen Listen entfernen,
     * item.scheduled bereinigen.
     */
    public static void clean(SQLrepo repo) {
        LocalDate today = LocalDate.now();
        LocalDate gestern = today.minusDays(1);

        // Gestrige todoList laden und Slots auswerten
        todoList liste = repo.fetch(Table.TODOS, Map.of("date", gestern.toString()));
        if (liste != null && liste.timeSlots != null) {
            processSlots(liste.timeSlots, gestern, repo);
        }

        // Alle vergangenen Listen entfernen (Datum < heute)
        cleanOrphanedLists(repo, today);

        // Vergangene scheduled-Einträge aus Items bereinigen
        cleanScheduledDates(repo, today);
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
     * Entfernt vergangene Daten aus item.scheduled für alle Items.
     */
    private static void cleanScheduledDates(SQLrepo repo, LocalDate today) {
        List<Long> allItemIds = repo.lookups("items", Map.of("is_completed", "0"), "id");

        for (Long id : allItemIds) {
            trackedItem item = repo.fetch(Table.ITEMS, id);
            if (item == null || item.scheduled == null || item.scheduled.isEmpty()) continue;

            List<LocalDate> cleaned = new ArrayList<>();
            for (LocalDate d : item.scheduled) {
                if (!d.isBefore(today)) {
                    cleaned.add(d);
                }
            }

            // Nur schreiben wenn sich etwas geändert hat
            if (cleaned.size() != item.scheduled.size()) {
                item.scheduled = cleaned;
                repo.write(item);
            }
        }
    }

    /**
     * Rekursiv alle Slots durchgehen und trackedItem.update() aufrufen.
     */
    private static void processSlots(List<TimeSlot> slots, LocalDate day, SQLrepo repo) {
        for (TimeSlot slot : slots) {
            // Item updaten wenn vorhanden
            if (slot.item != null) {
                trackedItem item = repo.fetch(Table.ITEMS, slot.item);
                if (item != null) {
                    boolean completed = slot.completed != null && slot.completed;
                    item.update(completed, slot.workStart, slot.workEnd, day, repo);
                }
            }

            // Verschachtelte Slots (Tasks innerhalb Goal-Slots)
            if (slot.timeSlots != null) {
                processSlots(slot.timeSlots, day, repo);
            }
        }
    }
}
