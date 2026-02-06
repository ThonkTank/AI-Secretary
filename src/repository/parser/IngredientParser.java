package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.Ingredient;

public class IngredientParser {

    // ============== BUILDER (DB → Java) ==============

    public static Ingredient fromRow(Map<String, Object> row) {
        Ingredient ing = new Ingredient();

        ing.id = (Long) row.get("id");
        ing.name = (String) row.get("name");

        String foodGroupStr = (String) row.get("food_group");
        if (foodGroupStr != null) {
            ing.foodGroup = ParseUtils.safeEnum(Ingredient.FoodGroup.class, foodGroupStr);
        }

        ing.defaultUnit = (String) row.get("default_unit");
        ing.gramsPerUnit = row.get("grams_per_unit") instanceof Number n ? n.intValue() : 100;

        // Nährwerte
        ing.caloriesPer100 = row.get("calories_per_100") instanceof Number n ? n.intValue() : 0;
        ing.proteinPer100 = row.get("protein_per_100") instanceof Number n ? n.intValue() : 0;
        ing.carbsPer100 = row.get("carbs_per_100") instanceof Number n ? n.intValue() : 0;
        ing.fatPer100 = row.get("fat_per_100") instanceof Number n ? n.intValue() : 0;
        ing.fiberPer100 = row.get("fiber_per_100") instanceof Number n ? n.intValue() : 0;

        ing.shelfLifeDays = row.get("shelf_life_days") instanceof Number n ? n.intValue() : 0;
        ing.requiresRefrigeration = Boolean.TRUE.equals(row.get("requires_refrigeration"));

        // Einkaufs-Eigenschaften
        ing.isWholeUnit = Boolean.TRUE.equals(row.get("is_whole_unit"));
        ing.isPerishable = Boolean.TRUE.equals(row.get("is_perishable"));

        return ing;
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
            case "grams_per_unit", "calories_per_100", "protein_per_100", "carbs_per_100",
                 "fat_per_100", "fiber_per_100", "shelf_life_days" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "requires_refrigeration", "is_whole_unit", "is_perishable" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Ingredient ing) {
        ContentValues cv = new ContentValues();

        if (ing.name != null) cv.put("name", ing.name);
        if (ing.foodGroup != null) cv.put("food_group", ing.foodGroup.name());
        if (ing.defaultUnit != null) cv.put("default_unit", ing.defaultUnit);
        cv.put("grams_per_unit", ing.gramsPerUnit);

        cv.put("calories_per_100", ing.caloriesPer100);
        cv.put("protein_per_100", ing.proteinPer100);
        cv.put("carbs_per_100", ing.carbsPer100);
        cv.put("fat_per_100", ing.fatPer100);
        cv.put("fiber_per_100", ing.fiberPer100);

        cv.put("shelf_life_days", ing.shelfLifeDays);
        cv.put("requires_refrigeration", ing.requiresRefrigeration ? 1 : 0);

        // Einkaufs-Eigenschaften
        cv.put("is_whole_unit", ing.isWholeUnit ? 1 : 0);
        cv.put("is_perishable", ing.isPerishable ? 1 : 0);

        if (ing.id != null) {
            db.update("ingredients", cv, "id = ?", new String[]{String.valueOf(ing.id)});
        } else {
            long newId = db.insert("ingredients", null, cv);
            ing.id = newId;
        }
    }
}
