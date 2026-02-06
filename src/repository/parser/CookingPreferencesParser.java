package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import entities.CookingPreferences;

public class CookingPreferencesParser {

    // ============== BUILDER (DB → Java) ==============

    public static CookingPreferences fromRow(Map<String, Object> row) {
        CookingPreferences p = new CookingPreferences();

        p.id = (Long) row.get("id");
        p.maxBreakfastCookingPerWeek = row.get("max_breakfast_cooking") instanceof Number n ? n.intValue() : 2;
        p.maxLunchCookingPerWeek = row.get("max_lunch_cooking") instanceof Number n ? n.intValue() : 3;
        p.maxDinnerCookingPerWeek = row.get("max_dinner_cooking") instanceof Number n ? n.intValue() : 3;
        p.quickPrepMaxMinutes = row.get("quick_prep_max_minutes") instanceof Number n ? n.intValue() : 15;

        p.breakfastCookingDays = parseDays((String) row.get("breakfast_cooking_days"));
        p.lunchCookingDays = parseDays((String) row.get("lunch_cooking_days"));
        p.dinnerCookingDays = parseDays((String) row.get("dinner_cooking_days"));

        return p;
    }

    private static Set<DayOfWeek> parseDays(String str) {
        if (str == null || str.isEmpty()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(str.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(ParseUtils::safeDayOfWeek)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
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
            case "max_breakfast_cooking", "max_lunch_cooking", "max_dinner_cooking", "quick_prep_max_minutes" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, CookingPreferences p) {
        ContentValues cv = new ContentValues();

        cv.put("max_breakfast_cooking", p.maxBreakfastCookingPerWeek);
        cv.put("max_lunch_cooking", p.maxLunchCookingPerWeek);
        cv.put("max_dinner_cooking", p.maxDinnerCookingPerWeek);
        cv.put("quick_prep_max_minutes", p.quickPrepMaxMinutes);

        if (p.breakfastCookingDays != null) {
            cv.put("breakfast_cooking_days", formatDays(p.breakfastCookingDays));
        }
        if (p.lunchCookingDays != null) {
            cv.put("lunch_cooking_days", formatDays(p.lunchCookingDays));
        }
        if (p.dinnerCookingDays != null) {
            cv.put("dinner_cooking_days", formatDays(p.dinnerCookingDays));
        }

        if (p.id != null) {
            db.update("cooking_preferences", cv, "id = ?", new String[]{String.valueOf(p.id)});
        } else {
            long newId = db.insert("cooking_preferences", null, cv);
            p.id = newId;
        }
    }

    private static String formatDays(Set<DayOfWeek> days) {
        return days.stream()
            .map(DayOfWeek::name)
            .collect(Collectors.joining(","));
    }
}
