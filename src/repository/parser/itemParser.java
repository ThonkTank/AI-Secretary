package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

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

        // Planung (min/max Duration)
        item.minDurationValue = typed.get("min_duration_value") instanceof Number n ? n.intValue() : 0;
        String minUnit = (String) typed.get("min_duration_unit");
        if (minUnit != null) item.minDurationUnit = trackedItem.DurationUnit.valueOf(minUnit);
        item.maxDurationValue = typed.get("max_duration_value") instanceof Number n ? n.intValue() : 0;
        String maxUnit = (String) typed.get("max_duration_unit");
        if (maxUnit != null) item.maxDurationUnit = trackedItem.DurationUnit.valueOf(maxUnit);
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
        String blockedDaysStr = (String) typed.get("blocked_days");
        if (blockedDaysStr != null) {
            item.blockedDays = java.util.Arrays.stream(blockedDaysStr.split(","))
                .map(java.time.LocalDate::parse)
                .collect(java.util.stream.Collectors.toSet());
        }

        // Deadline
        item.deadline = (java.time.LocalDate) typed.get("deadline");

        // Fortschritt
        item.progressCurrent = typed.get("progress_current") instanceof Number n ? n.intValue() : 0;
        item.progressTarget = typed.get("progress_target") instanceof Number n2 ? n2.intValue() : 0;
        item.progressUnit = (String) typed.get("progress_unit");
        item.progressPerRep = Boolean.TRUE.equals(typed.get("progress_per_rep"));
        item.progressLastPeriod = typed.get("progress_last_period") instanceof Number n ? n.intValue() : 0;
        item.timePerProgressUnit = typed.get("time_per_progress_unit") instanceof Number n ? n.intValue() : 0;
        item.progressTimingCount = typed.get("progress_timing_count") instanceof Number n ? n.intValue() : 0;

        // History
        item.currentStreak = typed.get("current_streak") instanceof Number n ? n.intValue() : 0;
        item.averageStreak = typed.get("average_streak") instanceof Number n ? n.intValue() : 0;
        item.nrOfStreaks = typed.get("nr_of_streaks") instanceof Number n ? n.intValue() : 0;
        item.totalCompletions = typed.get("total_completions") instanceof Number n ? n.intValue() : 0;
        item.minIntervalDays = typed.get("min_interval_days") instanceof Number n ? n.intValue() : 0;

        // Darstellung
        item.goalIcon = (String) typed.get("goal_icon");
        item.goalColor = (String) typed.get("goal_color");

        // FollowUp-Constraint
        item.requiredPredecessor = (Long) typed.get("required_predecessor");

        // Conditional Prerequisite
        item.conditionalPrerequisite = (Long) typed.get("conditional_prerequisite");
        item.prereqWindowDays = typed.get("prereq_window_days") instanceof Number n ? n.intValue() : null;

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
                 "rep_interval", "day_of_month", "required_predecessor",
                 "conditional_prerequisite" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());

            // Int-Felder
            case "completions", "min_duration_value", "max_duration_value", "daily_subgoal_limit",
                 "sequence_order", "current_streak", "average_streak",
                 "nr_of_streaks", "total_completions", "min_interval_days", "cooldown",
                 "progress_current", "progress_target", "progress_last_period",
                 "time_per_progress_unit", "progress_timing_count", "prereq_window_days" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());

            // Boolean-Felder
            case "is_completed", "complete_first", "is_block", "progress_per_rep" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());

            // LocalDate-Felder
            case "created", "last_completion", "deadline" ->
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
     * Konvertiert ein trackedItem zu ContentValues und persistiert in die DB.
     * INSERT wenn id == null, UPDATE wenn id vorhanden.
     * Setzt item.id nach INSERT auf die generierte ID.
     */
    public static void toRow(SQLiteDatabase db, trackedItem item) {
        ContentValues cv = new ContentValues();

        // Basic
        if (item.type != null) cv.put("type", item.type.name());
        if (item.title != null) cv.put("title", item.title);
        if (item.description != null) cv.put("description", item.description);
        if (item.created != null) cv.put("created", item.created.toString());

        // Completion
        if (item.lastCompletion != null) cv.put("last_completion", item.lastCompletion.toString());
        cv.put("completions", item.completions);
        cv.put("is_completed", (item.isCompleted != null && item.isCompleted) ? 1 : 0);

        // Repetition
        if (item.repetition != null) {
            cv.put("repetition_type", item.repetition.type.name());
            cv.put("repetition_unit", item.repetition.unit.name());
            cv.put("repetition_value", item.repetition.value);
            if (item.repetition.dayOfWeek != null) {
                cv.put("day_of_week", item.repetition.dayOfWeek.name());
            }
        }
        cv.put("complete_first", (item.completeFirst != null && item.completeFirst) ? 1 : 0);

        // Planung (min/max Duration)
        cv.put("min_duration_value", item.minDurationValue);
        if (item.minDurationUnit != null) cv.put("min_duration_unit", item.minDurationUnit.name());
        cv.put("max_duration_value", item.maxDurationValue);
        if (item.maxDurationUnit != null) cv.put("max_duration_unit", item.maxDurationUnit.name());
        if (item.priority != null) cv.put("priority", item.priority.name());
        if (item.prefTime != null) cv.put("pref_time", item.prefTime.toString());
        if (item.scheduled != null && !item.scheduled.isEmpty()) {
            cv.put("scheduled", item.scheduled.stream()
                .map(java.time.LocalDate::toString)
                .collect(Collectors.joining(",")));
        }
        cv.put("cooldown", item.cooldown);
        cv.put("progress_current", item.progressCurrent);
        cv.put("progress_target", item.progressTarget);
        if (item.progressUnit != null) cv.put("progress_unit", item.progressUnit);
        cv.put("progress_per_rep", item.progressPerRep ? 1 : 0);
        cv.put("progress_last_period", item.progressLastPeriod);
        cv.put("time_per_progress_unit", item.timePerProgressUnit);
        cv.put("progress_timing_count", item.progressTimingCount);
        if (item.deadline != null) cv.put("deadline", item.deadline.toString());
        if (item.blockedDays != null && !item.blockedDays.isEmpty()) {
            cv.put("blocked_days", item.blockedDays.stream()
                .map(java.time.LocalDate::toString)
                .collect(Collectors.joining(",")));
        }

        // History
        cv.put("current_streak", item.currentStreak);
        cv.put("average_streak", item.averageStreak);
        cv.put("nr_of_streaks", item.nrOfStreaks);
        cv.put("total_completions", item.totalCompletions);
        cv.put("min_interval_days", item.minIntervalDays);

        // Darstellung
        if (item.goalIcon != null) cv.put("goal_icon", item.goalIcon);
        if (item.goalColor != null) cv.put("goal_color", item.goalColor);

        // FollowUp-Constraint
        if (item.requiredPredecessor != null) cv.put("required_predecessor", item.requiredPredecessor);

        // Conditional Prerequisite
        if (item.conditionalPrerequisite != null) cv.put("conditional_prerequisite", item.conditionalPrerequisite);
        if (item.prereqWindowDays != null) cv.put("prereq_window_days", item.prereqWindowDays);

        // Relationen
        if (item.parent != null) cv.put("parent", item.parent);
        if (item.children != null && !item.children.isEmpty()) {
            cv.put("children", item.children.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        }
        if (item.followUps != null && !item.followUps.isEmpty()) {
            cv.put("followups", item.followUps.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(",")));
        }

        // Persistieren
        if (item.id != null) {
            db.update("items", cv, "id = ?", new String[]{String.valueOf(item.id)});
        } else {
            long newId = db.insert("items", null, cv);
            item.id = newId;
        }
    }

}
