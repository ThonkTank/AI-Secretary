package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.WeeklyFoodTarget;

public class WeeklyFoodTargetParser {

    // ============== BUILDER (DB → Java) ==============

    public static WeeklyFoodTarget fromRow(Map<String, Object> row) {
        WeeklyFoodTarget t = new WeeklyFoodTarget();

        t.id = (Long) row.get("id");
        t.weekKey = (String) row.get("week_key");

        // Zielmengen
        t.grainGrams = row.get("grain_grams") instanceof Number n ? n.intValue() : 0;
        t.potatoGrams = row.get("potato_grams") instanceof Number n ? n.intValue() : 0;
        t.vegetableGrams = row.get("vegetable_grams") instanceof Number n ? n.intValue() : 0;
        t.fruitGrams = row.get("fruit_grams") instanceof Number n ? n.intValue() : 0;
        t.dairyGrams = row.get("dairy_grams") instanceof Number n ? n.intValue() : 0;
        t.meatGrams = row.get("meat_grams") instanceof Number n ? n.intValue() : 0;
        t.fishGrams = row.get("fish_grams") instanceof Number n ? n.intValue() : 0;
        t.eggGrams = row.get("egg_grams") instanceof Number n ? n.intValue() : 0;
        t.fatGrams = row.get("fat_grams") instanceof Number n ? n.intValue() : 0;
        t.legumeGrams = row.get("legume_grams") instanceof Number n ? n.intValue() : 0;
        t.nutGrams = row.get("nut_grams") instanceof Number n ? n.intValue() : 0;

        // Geplante Mengen
        t.grainPlanned = row.get("grain_planned") instanceof Number n ? n.intValue() : 0;
        t.potatoPlanned = row.get("potato_planned") instanceof Number n ? n.intValue() : 0;
        t.vegetablePlanned = row.get("vegetable_planned") instanceof Number n ? n.intValue() : 0;
        t.fruitPlanned = row.get("fruit_planned") instanceof Number n ? n.intValue() : 0;
        t.dairyPlanned = row.get("dairy_planned") instanceof Number n ? n.intValue() : 0;
        t.meatPlanned = row.get("meat_planned") instanceof Number n ? n.intValue() : 0;
        t.fishPlanned = row.get("fish_planned") instanceof Number n ? n.intValue() : 0;
        t.eggPlanned = row.get("egg_planned") instanceof Number n ? n.intValue() : 0;
        t.fatPlanned = row.get("fat_planned") instanceof Number n ? n.intValue() : 0;
        t.legumePlanned = row.get("legume_planned") instanceof Number n ? n.intValue() : 0;
        t.nutPlanned = row.get("nut_planned") instanceof Number n ? n.intValue() : 0;

        return t;
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
            case "grain_grams", "potato_grams", "vegetable_grams", "fruit_grams", "dairy_grams",
                 "meat_grams", "fish_grams", "egg_grams", "fat_grams", "legume_grams", "nut_grams",
                 "grain_planned", "potato_planned", "vegetable_planned", "fruit_planned", "dairy_planned",
                 "meat_planned", "fish_planned", "egg_planned", "fat_planned", "legume_planned", "nut_planned" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, WeeklyFoodTarget t) {
        ContentValues cv = new ContentValues();

        if (t.weekKey != null) cv.put("week_key", t.weekKey);

        // Zielmengen
        cv.put("grain_grams", t.grainGrams);
        cv.put("potato_grams", t.potatoGrams);
        cv.put("vegetable_grams", t.vegetableGrams);
        cv.put("fruit_grams", t.fruitGrams);
        cv.put("dairy_grams", t.dairyGrams);
        cv.put("meat_grams", t.meatGrams);
        cv.put("fish_grams", t.fishGrams);
        cv.put("egg_grams", t.eggGrams);
        cv.put("fat_grams", t.fatGrams);
        cv.put("legume_grams", t.legumeGrams);
        cv.put("nut_grams", t.nutGrams);

        // Geplante Mengen
        cv.put("grain_planned", t.grainPlanned);
        cv.put("potato_planned", t.potatoPlanned);
        cv.put("vegetable_planned", t.vegetablePlanned);
        cv.put("fruit_planned", t.fruitPlanned);
        cv.put("dairy_planned", t.dairyPlanned);
        cv.put("meat_planned", t.meatPlanned);
        cv.put("fish_planned", t.fishPlanned);
        cv.put("egg_planned", t.eggPlanned);
        cv.put("fat_planned", t.fatPlanned);
        cv.put("legume_planned", t.legumePlanned);
        cv.put("nut_planned", t.nutPlanned);

        if (t.id != null) {
            db.update("weekly_food_targets", cv, "id = ?", new String[]{String.valueOf(t.id)});
        } else {
            long newId = db.insert("weekly_food_targets", null, cv);
            t.id = newId;
        }
    }
}
