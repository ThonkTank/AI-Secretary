package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.WeeklyFoodTarget;

public class WeeklyFoodTargetParser {

    public static WeeklyFoodTarget fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        WeeklyFoodTarget t = new WeeklyFoodTarget();

        t.id = (Long) typed.get("id");
        t.periodKey = (String) typed.get("period_key");

        t.grainGrams = ParseUtils.safeInt(typed.get("grain_grams"), 0);
        t.potatoGrams = ParseUtils.safeInt(typed.get("potato_grams"), 0);
        t.vegetableGrams = ParseUtils.safeInt(typed.get("vegetable_grams"), 0);
        t.fruitGrams = ParseUtils.safeInt(typed.get("fruit_grams"), 0);
        t.dairyGrams = ParseUtils.safeInt(typed.get("dairy_grams"), 0);
        t.meatGrams = ParseUtils.safeInt(typed.get("meat_grams"), 0);
        t.fishGrams = ParseUtils.safeInt(typed.get("fish_grams"), 0);
        t.eggGrams = ParseUtils.safeInt(typed.get("egg_grams"), 0);
        t.fatGrams = ParseUtils.safeInt(typed.get("fat_grams"), 0);
        t.legumeGrams = ParseUtils.safeInt(typed.get("legume_grams"), 0);
        t.nutGrams = ParseUtils.safeInt(typed.get("nut_grams"), 0);

        t.grainPlanned = ParseUtils.safeInt(typed.get("grain_planned"), 0);
        t.potatoPlanned = ParseUtils.safeInt(typed.get("potato_planned"), 0);
        t.vegetablePlanned = ParseUtils.safeInt(typed.get("vegetable_planned"), 0);
        t.fruitPlanned = ParseUtils.safeInt(typed.get("fruit_planned"), 0);
        t.dairyPlanned = ParseUtils.safeInt(typed.get("dairy_planned"), 0);
        t.meatPlanned = ParseUtils.safeInt(typed.get("meat_planned"), 0);
        t.fishPlanned = ParseUtils.safeInt(typed.get("fish_planned"), 0);
        t.eggPlanned = ParseUtils.safeInt(typed.get("egg_planned"), 0);
        t.fatPlanned = ParseUtils.safeInt(typed.get("fat_planned"), 0);
        t.legumePlanned = ParseUtils.safeInt(typed.get("legume_planned"), 0);
        t.nutPlanned = ParseUtils.safeInt(typed.get("nut_planned"), 0);

        return t;
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
            case "grain_grams", "potato_grams", "vegetable_grams", "fruit_grams",
                 "dairy_grams", "meat_grams", "fish_grams", "egg_grams",
                 "fat_grams", "legume_grams", "nut_grams",
                 "grain_planned", "potato_planned", "vegetable_planned", "fruit_planned",
                 "dairy_planned", "meat_planned", "fish_planned", "egg_planned",
                 "fat_planned", "legume_planned", "nut_planned" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, WeeklyFoodTarget t) {
        ContentValues cv = new ContentValues();

        if (t.periodKey != null) cv.put("period_key", t.periodKey);
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
            t.id = db.insert("weekly_food_targets", null, cv);
        }
    }
}
