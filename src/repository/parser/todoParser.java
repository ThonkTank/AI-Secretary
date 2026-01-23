package repository.parser;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import entities.todoList;
import entities.todoList.TimeSlot;

public class todoParser {

    /**
     * Konvertiert eine DB-Zeile (todos) zu einem todoList Objekt.
     * Lädt zugehörige TimeSlots aus der time_slots-Tabelle.
     */
    public static todoList fromRow(Map<String, Object> row, Connection conn) throws SQLException {
        Map<String, Object> typed = convertRow(row);
        todoList todo = new todoList();
        todo.id = (Long) typed.get("id");
        todo.date = (LocalDate) typed.get("date");
        todo.start = (LocalTime) typed.get("start_time");
        todo.end = (LocalTime) typed.get("end_time");

        // TimeSlots aus eigener Tabelle laden
        if (todo.id != null) {
            todo.timeSlots = loadSlots(conn, todo.id);
        }
        return todo;
    }

    /**
     * Lädt alle TimeSlots für eine todoList und rekonstruiert die Verschachtelung.
     */
    private static List<TimeSlot> loadSlots(Connection conn, long todoId) throws SQLException {
        List<Map<String, Object>> allSlots = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id, parent_slot_id, start_time, end_time, item_id, completed FROM time_slots WHERE todo_id = ? ORDER BY id")) {
            pstmt.setLong(1, todoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("id", rs.getLong("id"));
                slot.put("parent_slot_id", rs.getObject("parent_slot_id"));
                slot.put("start_time", rs.getString("start_time"));
                slot.put("end_time", rs.getString("end_time"));
                slot.put("item_id", rs.getObject("item_id"));
                slot.put("completed", rs.getInt("completed"));
                allSlots.add(slot);
            }
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
     * Persistiert ein todoList Objekt in die DB (todos + time_slots).
     * INSERT wenn id == null, UPDATE wenn id vorhanden.
     */
    public static void toRow(Connection conn, todoList todo) throws SQLException {
        Map<String, String> row = new LinkedHashMap<>();
        if (todo.date != null) row.put("date", todo.date.toString());
        if (todo.start != null) row.put("start_time", todo.start.toString());
        if (todo.end != null) row.put("end_time", todo.end.toString());

        if (todo.id == null) {
            String columns = String.join(", ", row.keySet());
            String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO todos (" + columns + ") VALUES (" + placeholders + ")",
                    Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                for (String value : row.values()) pstmt.setString(i++, value);
                pstmt.executeUpdate();
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) todo.id = keys.getLong(1);
                }
            }
        } else {
            // Alte Slots löschen vor UPDATE
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM time_slots WHERE todo_id = ?")) {
                del.setLong(1, todo.id);
                del.executeUpdate();
            }

            String setClause = row.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(", "));
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE todos SET " + setClause + " WHERE id = ?")) {
                int i = 1;
                for (String value : row.values()) pstmt.setString(i++, value);
                pstmt.setLong(i, todo.id);
                pstmt.executeUpdate();
            }
        }

        // TimeSlots in eigene Tabelle persistieren
        if (todo.timeSlots != null && !todo.timeSlots.isEmpty()) {
            persistSlots(conn, todo.id, null, todo.timeSlots);
        }
    }

    /**
     * Persistiert TimeSlots rekursiv (Goal-Slots → Task-Slots).
     */
    private static void persistSlots(Connection conn, long todoId, Long parentSlotId, List<TimeSlot> slots) throws SQLException {
        for (TimeSlot slot : slots) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO time_slots (todo_id, parent_slot_id, start_time, end_time, item_id, completed) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setLong(1, todoId);
                if (parentSlotId != null) pstmt.setLong(2, parentSlotId);
                else pstmt.setNull(2, Types.INTEGER);
                pstmt.setString(3, slot.start != null ? slot.start.toString() : null);
                pstmt.setString(4, slot.end != null ? slot.end.toString() : null);
                if (slot.item != null) pstmt.setLong(5, slot.item);
                else pstmt.setNull(5, Types.INTEGER);
                pstmt.setInt(6, (slot.completed != null && slot.completed) ? 1 : 0);
                pstmt.executeUpdate();

                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) slot.id = keys.getLong(1);
                }
            }

            // Verschachtelte Slots (Tasks innerhalb eines Goals)
            if (slot.timeSlots != null && !slot.timeSlots.isEmpty()) {
                persistSlots(conn, todoId, slot.id, slot.timeSlots);
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
