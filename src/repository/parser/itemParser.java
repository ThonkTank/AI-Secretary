package repository.parser;

import java.sql.*;
import java.util.Map;
import java.util.stream.Collectors;

import entities.trackedItem;

public class itemParser {
    // ============== BUILDER (DB → Java) ==============

    /**
     * Baut ein trackedItem aus einer DB-Zeile.
     * Inverse von toRow().
     */
    public static trackedItem fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        trackedItem item = new trackedItem();

        // ID
        item.id = (Long) typed.get("id");

        // Basic
        String typeStr = (String) typed.get("type");
        if (typeStr != null) {
            item.type = trackedItem.ItemType.valueOf(typeStr.toUpperCase());
        }
        item.title = (String) typed.get("title");
        item.description = (String) typed.get("description");
        item.created = (java.time.LocalDate) typed.get("created");

        // Completion
        item.lastCompletion = (java.time.LocalDate) typed.get("last_completion");
        item.completions = typed.get("completions") instanceof Number n ? n.intValue() : 0;
        item.isCompleted = Boolean.TRUE.equals(typed.get("is_completed"));

        // Repetition
        String repType = (String) typed.get("repetition_type");
        if (repType != null) {
            item.repetition = new trackedItem.Repetition();
            item.repetition.type = trackedItem.RepetitionType.valueOf(repType);
            item.repetition.unit = trackedItem.RepUnits.valueOf((String) typed.get("repetition_unit"));
            item.repetition.value = typed.get("repetition_value") instanceof Number n ? n.intValue() : 0;
            String dow = (String) typed.get("day_of_week");
            if (dow != null) {
                item.repetition.dayOfWeek = java.time.DayOfWeek.valueOf(dow);
            }
        }
        item.completeFirst = Boolean.TRUE.equals(typed.get("complete_first"));

        // Timeframe
        java.time.LocalDate repStart = (java.time.LocalDate) typed.get("next_rep_start");
        if (repStart != null) {
            java.time.LocalDate repEnd = (java.time.LocalDate) typed.get("next_rep_end");
            item.nextRepetition = (repEnd != null)
                ? new trackedItem.Timeframe(repStart, repEnd)
                : new trackedItem.Timeframe(repStart);
        }

        // Planung
        item.timeToComplete = typed.get("time_to_complete") instanceof Number n ? n.intValue() : 0;
        String prioStr = (String) typed.get("priority");
        if (prioStr != null) {
            item.priority = trackedItem.Priority.valueOf(prioStr);
        }
        item.prefTime = (java.time.LocalTime) typed.get("pref_time");
        String scheduledStr = (String) typed.get("scheduled");
        if (scheduledStr != null) {
            item.scheduled = java.util.Arrays.stream(scheduledStr.split(","))
                .map(java.time.LocalDate::parse)
                .collect(java.util.stream.Collectors.toList());
        }
        item.cooldown = typed.get("cooldown") instanceof Number n ? n.intValue() : 0;

        // History
        item.currentStreak = typed.get("current_streak") instanceof Number n ? n.intValue() : 0;
        item.averageStreak = typed.get("average_streak") instanceof Number n ? n.intValue() : 0;
        item.nrOfStreaks = typed.get("nr_of_streaks") instanceof Number n ? n.intValue() : 0;
        item.totalCompletions = typed.get("total_completions") instanceof Number n ? n.intValue() : 0;
        item.minIntervalDays = typed.get("min_interval_days") instanceof Number n ? n.intValue() : 0;

        // Relationen
        item.parent = (Long) typed.get("parent");
        String childrenStr = (String) typed.get("children");
        if (childrenStr != null) {
            item.children = java.util.Arrays.stream(childrenStr.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList());
        }
        String followupsStr = (String) typed.get("followups");
        if (followupsStr != null) {
            item.followUps = new java.util.LinkedHashMap<>();
            for (String pair : followupsStr.split(",")) {
                String[] parts = pair.trim().split(":");
                item.followUps.put(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
            }
        }

        return item;
    }

    // ============== CONVERTER (rohe DB-Werte → korrekte Java-Typen) ==============

    /**
     * Konvertiert eine rohe DB-Zeile: jeder Wert wird zum korrekten Java-Typ.
     * z.B. "id" → Long, "created" → LocalDate, "is_completed" → Boolean, etc.
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
            // Long-Felder
            case "id", "parent", "repetition_value", "required_completions",
                 "rep_interval", "day_of_month" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());

            // Int-Felder
            case "completions", "time_to_complete", "daily_subgoal_limit",
                 "sequence_order", "current_streak", "average_streak",
                 "nr_of_streaks", "total_completions", "min_interval_days", "cooldown" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());

            // Boolean-Felder
            case "is_completed", "complete_first", "is_block" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());

            // LocalDate-Felder
            case "created", "last_completion", "next_rep_start", "next_rep_end" ->
                java.time.LocalDate.parse(v.toString());

            // LocalTime-Felder
            case "pref_time" ->
                java.time.LocalTime.parse(v.toString());

            // String-Felder (type, title, description, priority, etc.)
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    /**
     * Konvertiert ein trackedItem zu einer Map<Spaltenname, Wert> und persistiert direkt in die DB.
     * INSERT wenn id == null, UPDATE wenn id vorhanden.
     * Setzt item.id nach INSERT auf die generierte ID.
     */
    public static void toRow(Connection conn, trackedItem item) throws SQLException {
        Map<String, String> row = new java.util.LinkedHashMap<>();

        // Basic
        if (item.type != null) row.put("type", item.type.name().substring(0, 1) + item.type.name().substring(1).toLowerCase());
        if (item.title != null) row.put("title", item.title);
        if (item.description != null) row.put("description", item.description);
        if (item.created != null) row.put("created", item.created.toString());

        // Completion
        if (item.lastCompletion != null) row.put("last_completion", item.lastCompletion.toString());
        row.put("completions", String.valueOf(item.completions));
        row.put("is_completed", (item.isCompleted != null && item.isCompleted) ? "1" : "0");

        // Repetition
        if (item.repetition != null) {
            row.put("repetition_type", item.repetition.type.name());
            row.put("repetition_unit", item.repetition.unit.name());
            row.put("repetition_value", String.valueOf(item.repetition.value));
            if (item.repetition.dayOfWeek != null) {
                row.put("day_of_week", item.repetition.dayOfWeek.name());
            }
        }
        row.put("complete_first", (item.completeFirst != null && item.completeFirst) ? "1" : "0");

        // Timeframe
        if (item.nextRepetition != null) {
            row.put("next_rep_start", item.nextRepetition.start.toString());
            if (item.nextRepetition.end != null) {
                row.put("next_rep_end", item.nextRepetition.end.toString());
            }
        }

        // Planung
        row.put("time_to_complete", String.valueOf(item.timeToComplete));
        if (item.priority != null) row.put("priority", item.priority.name());
        if (item.prefTime != null) row.put("pref_time", item.prefTime.toString());
        if (item.scheduled != null && !item.scheduled.isEmpty()) {
            row.put("scheduled", item.scheduled.stream()
                .map(java.time.LocalDate::toString)
                .collect(Collectors.joining(",")));
        }
        row.put("cooldown", String.valueOf(item.cooldown));

        // History
        row.put("current_streak", String.valueOf(item.currentStreak));
        row.put("average_streak", String.valueOf(item.averageStreak));
        row.put("nr_of_streaks", String.valueOf(item.nrOfStreaks));
        row.put("total_completions", String.valueOf(item.totalCompletions));
        row.put("min_interval_days", String.valueOf(item.minIntervalDays));

        // Relationen
        if (item.parent != null) row.put("parent", String.valueOf(item.parent));
        if (item.children != null && !item.children.isEmpty()) {
            row.put("children", item.children.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        }
        if (item.followUps != null && !item.followUps.isEmpty()) {
            row.put("followups", item.followUps.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(",")));
        }

        // Persistieren
        if (item.id != null) {
            String setClause = row.keySet().stream()
                .map(col -> col + " = ?")
                .collect(Collectors.joining(", "));
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE items SET " + setClause + " WHERE id = ?")) {
                int i = 1;
                for (String value : row.values()) pstmt.setString(i++, value);
                pstmt.setLong(i, item.id);
                pstmt.executeUpdate();
            }
        } else {
            String columns = String.join(", ", row.keySet());
            String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO items (" + columns + ") VALUES (" + placeholders + ")",
                    Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                for (String value : row.values()) pstmt.setString(i++, value);
                pstmt.executeUpdate();
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    keys.next();
                    item.id = keys.getLong(1);
                }
            }
        }
    }

}
