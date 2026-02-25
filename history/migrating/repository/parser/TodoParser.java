package repository.parser;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import entities.TodoList;
import entities.TodoList.TimeSlot;

public class TodoParser {

    /**
     * Konvertiert eine DB-Zeile (todos) zu einem TodoList Objekt.
     * Laedt zugehoerige TimeSlots aus der time_slots-Tabelle.
     */
    public static TodoList fromRow(Map<String, Object> row, SQLiteDatabase db) {
        Map<String, Object> typed = convertRow(row);
        TodoList todo = new TodoList();
        todo.id = (Long) typed.get("id");
        todo.date = (LocalDate) typed.get("date");
        todo.start = (LocalTime) typed.get("start_time");
        todo.end = (LocalTime) typed.get("end_time");

        // TimeSlots aus eigener Tabelle laden
        if (todo.id != null) {
            todo.timeSlots = loadSlots(db, todo.id);
        }
        return todo;
    }

    /**
     * Laedt alle TimeSlots fuer eine TodoList und rekonstruiert die Verschachtelung.
     */
    private static List<TimeSlot> loadSlots(SQLiteDatabase db, long todoId) {
        List<Map<String, Object>> allSlots = new ArrayList<>();
        Cursor cursor = db.query("time_slots",
            new String[]{"id", "parent_slot_id", "start_time", "end_time", "item_id", "completed", "is_calendar_event", "calendar_title", "work_start", "work_end", "progress_delta", "previous_completed_item_id", "chain_id"},
            "todo_id = ?", new String[]{String.valueOf(todoId)},
            null, null, "id ASC");
        try {
            while (cursor.moveToNext()) {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("id", cursor.getLong(0));
                slot.put("parent_slot_id", cursor.isNull(1) ? null : cursor.getLong(1));
                slot.put("start_time", cursor.isNull(2) ? null : cursor.getString(2));
                slot.put("end_time", cursor.isNull(3) ? null : cursor.getString(3));
                slot.put("item_id", cursor.isNull(4) ? null : cursor.getLong(4));
                slot.put("completed", cursor.getInt(5));
                slot.put("is_calendar_event", cursor.getInt(6));
                slot.put("calendar_title", cursor.isNull(7) ? null : cursor.getString(7));
                slot.put("work_start", cursor.isNull(8) ? null : cursor.getString(8));
                slot.put("work_end", cursor.isNull(9) ? null : cursor.getString(9));
                slot.put("progress_delta", cursor.getInt(10));
                slot.put("previous_completed_item_id", cursor.isNull(11) ? null : cursor.getLong(11));
                slot.put("chain_id", cursor.isNull(12) ? null : cursor.getLong(12));
                allSlots.add(slot);
            }
        } finally {
            cursor.close();
        }

        // Alle TimeSlot-Objekte erstellen
        Map<Long, TimeSlot> slotMap = new LinkedHashMap<>();
        for (Map<String, Object> r : allSlots) {
            TimeSlot ts = new TimeSlot();
            ts.id = (Long) r.get("id");
            String startStr = (String) r.get("start_time");
            if (startStr != null) ts.start = LocalTime.parse(startStr);
            String endStr = (String) r.get("end_time");
            if (endStr != null) ts.end = LocalTime.parse(endStr);
            Object itemId = r.get("item_id");
            if (itemId != null) ts.item = ((Number) itemId).longValue();
            ts.completed = ((Number) r.get("completed")).intValue() == 1;
            ts.isCalendarEvent = ((Number) r.get("is_calendar_event")).intValue() == 1;
            ts.calendarTitle = (String) r.get("calendar_title");
            String workStartStr = (String) r.get("work_start");
            if (workStartStr != null) ts.workStart = LocalTime.parse(workStartStr);
            String workEndStr = (String) r.get("work_end");
            if (workEndStr != null) ts.workEnd = LocalTime.parse(workEndStr);
            ts.progressDelta = ((Number) r.get("progress_delta")).intValue();
            Object prevItemId = r.get("previous_completed_item_id");
            if (prevItemId != null) ts.previousCompletedItemId = ((Number) prevItemId).longValue();
            Object chainIdVal = r.get("chain_id");
            if (chainIdVal != null) ts.chainId = ((Number) chainIdVal).longValue();
            slotMap.put(ts.id, ts);
        }

        // Hierarchie aufbauen (parent_slot_id == null → Top-Level)
        List<TimeSlot> topLevel = new ArrayList<>();
        for (Map<String, Object> r : allSlots) {
            Long id = (Long) r.get("id");
            Object parentId = r.get("parent_slot_id");
            TimeSlot ts = slotMap.get(id);

            if (parentId == null) {
                topLevel.add(ts);
            } else {
                long pid = ((Number) parentId).longValue();
                TimeSlot parent = slotMap.get(pid);
                if (parent != null) {
                    if (parent.timeSlots == null) parent.timeSlots = new ArrayList<>();
                    parent.timeSlots.add(ts);
                }
            }
        }

        return topLevel;
    }

    /**
     * Persistiert ein TodoList Objekt in die DB (todos + time_slots).
     * INSERT wenn id == null, UPDATE wenn id vorhanden.
     * Nutzt eine Transaction fuer atomare Persistierung.
     */
    public static void toRow(SQLiteDatabase db, TodoList todo) {
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            if (todo.date != null) cv.put("date", todo.date.toString());
            if (todo.start != null) cv.put("start_time", todo.start.toString());
            if (todo.end != null) cv.put("end_time", todo.end.toString());

            if (todo.id == null) {
                long newId = db.insert("todos", null, cv);
                todo.id = newId;
            } else {
                // Alte Slots loeschen vor UPDATE
                db.delete("time_slots", "todo_id = ?", new String[]{String.valueOf(todo.id)});
                db.update("todos", cv, "id = ?", new String[]{String.valueOf(todo.id)});
            }

            // TimeSlots in eigene Tabelle persistieren
            if (todo.timeSlots != null && !todo.timeSlots.isEmpty()) {
                persistSlots(db, todo.id, null, todo.timeSlots);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Persistiert TimeSlots rekursiv (Goal-Slots → Task-Slots).
     */
    private static void persistSlots(SQLiteDatabase db, long todoId, Long parentSlotId, List<TimeSlot> slots) {
        for (TimeSlot slot : slots) {
            ContentValues cv = new ContentValues();
            cv.put("todo_id", todoId);
            if (parentSlotId != null) {
                cv.put("parent_slot_id", parentSlotId);
            }
            cv.put("start_time", slot.start != null ? slot.start.toString() : null);
            cv.put("end_time", slot.end != null ? slot.end.toString() : null);
            if (slot.item != null) {
                cv.put("item_id", slot.item);
            }
            cv.put("completed", (slot.completed != null && slot.completed) ? 1 : 0);
            cv.put("is_calendar_event", (slot.isCalendarEvent != null && slot.isCalendarEvent) ? 1 : 0);
            cv.put("calendar_title", slot.calendarTitle);
            cv.put("work_start", slot.workStart != null ? slot.workStart.toString() : null);
            cv.put("work_end", slot.workEnd != null ? slot.workEnd.toString() : null);
            cv.put("progress_delta", slot.progressDelta != null ? slot.progressDelta : 0);
            if (slot.previousCompletedItemId != null) {
                cv.put("previous_completed_item_id", slot.previousCompletedItemId);
            }
            if (slot.chainId != null) {
                cv.put("chain_id", slot.chainId);
            }

            long newSlotId = db.insert("time_slots", null, cv);
            slot.id = newSlotId;

            // Verschachtelte Slots (Tasks innerhalb eines Goals)
            if (slot.timeSlots != null && !slot.timeSlots.isEmpty()) {
                persistSlots(db, todoId, slot.id, slot.timeSlots);
            }
        }
    }

    // --- Converter (rohe DB-Werte → korrekte Java-Typen) ---

    /**
     * Konvertiert eine rohe DB-Zeile der todos-Tabelle zum korrekten Typ.
     * z.B. "id" → Long, "date" → LocalDate, "start_time" → LocalTime
     */
    public static Map<String, Object> convertRow(Map<String, Object> raw) {
        Map<String, Object> typed = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            typed.put(entry.getKey(), convertValue(entry.getKey(), entry.getValue()));
        }
        return typed;
    }

    public static Object convertValue(String column, Object v) {
        if (v == null) return null;
        return switch (column) {
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "date" -> LocalDate.parse(v.toString());
            case "start_time", "end_time" -> LocalTime.parse(v.toString());
            default -> v.toString();
        };
    }

}
