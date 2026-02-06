package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import entities.MealSchedule;
import entities.MealType;

public class mealScheduleParser {

    // ============== BUILDER (DB → Java) ==============

    public static MealSchedule fromRow(Map<String, Object> row) {
        MealSchedule ms = new MealSchedule();

        ms.id = (Long) row.get("id");

        String dayStr = (String) row.get("day_of_week");
        if (dayStr != null) {
            ms.dayOfWeek = ParseUtils.safeDayOfWeek(dayStr);
        }

        String mealTypeStr = (String) row.get("meal_type");
        if (mealTypeStr != null) {
            ms.mealType = ParseUtils.safeEnum(MealType.class, mealTypeStr);
        }

        ms.scheduledTime = (LocalTime) row.get("scheduled_time");
        ms.isEnabled = Boolean.TRUE.equals(row.get("is_enabled"));

        return ms;
    }

    // ============== CONVERTER ==============

    public static Map<String, Object> convertRow(Map<String, Object> raw) {
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            typed.put(entry.getKey(), convertValue(entry.getKey(), entry.getValue()));
        }
        return typed;
    }

    public static Object convertValue(String column, Object v) {
        if (v == null) return null;
        return switch (column) {
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "is_enabled" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "scheduled_time" -> LocalTime.parse(v.toString());
            default -> v.toString();  // day_of_week, meal_type als String
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, MealSchedule ms) {
        ContentValues cv = new ContentValues();

        if (ms.dayOfWeek != null) cv.put("day_of_week", ms.dayOfWeek.name());
        if (ms.mealType != null) cv.put("meal_type", ms.mealType.name());
        if (ms.scheduledTime != null) cv.put("scheduled_time", ms.scheduledTime.toString());
        cv.put("is_enabled", ms.isEnabled ? 1 : 0);

        if (ms.id != null) {
            db.update("meal_schedules", cv, "id = ?", new String[]{String.valueOf(ms.id)});
        } else {
            long newId = db.insert("meal_schedules", null, cv);
            ms.id = newId;
        }
    }
}
