package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import entities.CookingPreferences;

public class CookingPreferencesParser {

    public static CookingPreferences fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        CookingPreferences p = new CookingPreferences();

        p.id = (Long) typed.get("id");
        p.maxBreakfastCooking = ParseUtils.safeInt(typed.get("max_breakfast_cooking"), 3);
        p.maxLunchCooking = ParseUtils.safeInt(typed.get("max_lunch_cooking"), 3);
        p.maxDinnerCooking = ParseUtils.safeInt(typed.get("max_dinner_cooking"), 3);
        p.maxSnackCooking = ParseUtils.safeInt(typed.get("max_snack_cooking"), 0);
        p.breakfastCookingDays = parseDays((String) typed.get("breakfast_cooking_days"));
        p.lunchCookingDays = parseDays((String) typed.get("lunch_cooking_days"));
        p.dinnerCookingDays = parseDays((String) typed.get("dinner_cooking_days"));
        p.snackCookingDays = parseDays((String) typed.get("snack_cooking_days"));
        p.quickPrepMaxMinutes = ParseUtils.safeInt(typed.get("quick_prep_max_minutes"), 15);

        return p;
    }

    public static Map<String, Object> convertRow(Map<String, Object> raw) {
        Map<String, Object> typed = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            typed.put(e.getKey(), convertValue(e.getKey(), e.getValue()));
        }
        return typed;
    }

    public static Object convertValue(String column, Object v) {
        if (v == null) return null;
        return switch (column) {
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "max_breakfast_cooking", "max_lunch_cooking", "max_dinner_cooking",
                 "max_snack_cooking", "quick_prep_max_minutes" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, CookingPreferences p) {
        ContentValues cv = new ContentValues();

        cv.put("max_breakfast_cooking", p.maxBreakfastCooking);
        cv.put("max_lunch_cooking", p.maxLunchCooking);
        cv.put("max_dinner_cooking", p.maxDinnerCooking);
        cv.put("max_snack_cooking", p.maxSnackCooking);
        cv.put("breakfast_cooking_days", serializeDays(p.breakfastCookingDays));
        cv.put("lunch_cooking_days", serializeDays(p.lunchCookingDays));
        cv.put("dinner_cooking_days", serializeDays(p.dinnerCookingDays));
        cv.put("snack_cooking_days", serializeDays(p.snackCookingDays));
        cv.put("quick_prep_max_minutes", p.quickPrepMaxMinutes);

        if (p.id != null) {
            db.update("cooking_preferences", cv, "id = ?", new String[]{String.valueOf(p.id)});
        } else {
            p.id = db.insert("cooking_preferences", null, cv);
        }
    }

    // ============== SERIALISIERUNG ==============

    static Set<DayOfWeek> parseDays(String raw) {
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            DayOfWeek day = ParseUtils.safeDayOfWeek(part.trim());
            if (day != null) result.add(day);
        }
        return result;
    }

    static String serializeDays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "";
        return days.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
