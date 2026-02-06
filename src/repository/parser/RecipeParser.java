package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import entities.MealType;
import entities.Recipe;

public class RecipeParser {

    // ============== BUILDER (DB → Java) ==============

    public static Recipe fromRow(Map<String, Object> row) {
        Recipe r = new Recipe();

        r.id = (Long) row.get("id");
        r.title = (String) row.get("title");
        r.description = (String) row.get("description");
        r.instructions = (String) row.get("instructions");

        r.servings = row.get("servings") instanceof Number n ? n.intValue() : 2;
        r.prepTimeMinutes = row.get("prep_time_minutes") instanceof Number n ? n.intValue() : 0;
        r.cookTimeMinutes = row.get("cook_time_minutes") instanceof Number n ? n.intValue() : 0;

        r.requiresCooking = Boolean.TRUE.equals(row.get("requires_cooking"));

        String effortStr = (String) row.get("prep_effort");
        if (effortStr != null) {
            r.prepEffort = ParseUtils.safeEnum(Recipe.PrepEffort.class, effortStr);
        }

        r.servingsPerCooking = row.get("servings_per_cooking") instanceof Number n ? n.intValue() : 2;
        r.shelfLifeDays = row.get("shelf_life_days") instanceof Number n ? n.intValue() : 1;

        // Skalierung
        r.minServings = row.get("min_servings") instanceof Number n ? n.intValue() : 1;
        r.maxServings = row.get("max_servings") instanceof Number n ? n.intValue() : 10;
        String scalingStr = (String) row.get("scaling_precision");
        r.scalingPrecision = ParseUtils.safeEnum(Recipe.ScalingPrecision.class, scalingStr, Recipe.ScalingPrecision.ROUGH);

        String mealTypeStr = (String) row.get("meal_type");
        if (mealTypeStr != null) {
            r.mealType = ParseUtils.safeEnum(MealType.class, mealTypeStr);
        }

        // Ingredients als JSON String
        r.ingredients = parseIngredients((String) row.get("ingredients"));

        r.tags = (String) row.get("tags");
        r.lastUsed = (LocalDate) row.get("last_used");
        r.usageCount = row.get("usage_count") instanceof Number n ? n.intValue() : 0;
        r.isFavorite = Boolean.TRUE.equals(row.get("is_favorite"));

        r.totalCalories = row.get("total_calories") instanceof Number n ? n.intValue() : 0;
        r.totalProtein = row.get("total_protein") instanceof Number n ? n.intValue() : 0;
        r.totalCarbs = row.get("total_carbs") instanceof Number n ? n.intValue() : 0;
        r.totalFat = row.get("total_fat") instanceof Number n ? n.intValue() : 0;

        return r;
    }

    /**
     * Parst Zutaten aus JSON-String.
     * Format: [{"id":1,"name":"Hühnchen","amount":200,"unit":"g"},...]
     */
    private static java.util.List<Recipe.RecipeIngredient> parseIngredients(String json) {
        java.util.List<Recipe.RecipeIngredient> result = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return result;
        }

        // Einfaches Parsing ohne JSON-Library
        // Format: id:name:amount:unit,id:name:amount:unit,...
        // Oder JSON-ähnlich vereinfacht
        try {
            // Erwarte Format: 1|Hühnchen|200|g;2|Reis|100|g
            String[] parts = json.split(";");
            for (String part : parts) {
                String[] fields = part.split("\\|");
                if (fields.length >= 4) {
                    Long id = Long.parseLong(fields[0]);
                    String name = fields[1];
                    double amount = Double.parseDouble(fields[2]);
                    String unit = fields[3];
                    result.add(new Recipe.RecipeIngredient(id, name, amount, unit));
                }
            }
        } catch (Exception e) {
            // Bei Parsing-Fehlern leere Liste zurückgeben
        }
        return result;
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
            case "servings", "prep_time_minutes", "cook_time_minutes", "servings_per_cooking",
                 "shelf_life_days", "usage_count", "total_calories", "total_protein",
                 "total_carbs", "total_fat", "min_servings", "max_servings" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "requires_cooking", "is_favorite" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "last_used" -> LocalDate.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Recipe r) {
        ContentValues cv = new ContentValues();

        if (r.title != null) cv.put("title", r.title);
        if (r.description != null) cv.put("description", r.description);
        if (r.instructions != null) cv.put("instructions", r.instructions);

        cv.put("servings", r.servings);
        cv.put("prep_time_minutes", r.prepTimeMinutes);
        cv.put("cook_time_minutes", r.cookTimeMinutes);
        cv.put("requires_cooking", r.requiresCooking ? 1 : 0);

        if (r.prepEffort != null) cv.put("prep_effort", r.prepEffort.name());
        cv.put("servings_per_cooking", r.servingsPerCooking);
        cv.put("shelf_life_days", r.shelfLifeDays);

        // Skalierung
        cv.put("min_servings", r.minServings);
        cv.put("max_servings", r.maxServings);
        if (r.scalingPrecision != null) cv.put("scaling_precision", r.scalingPrecision.name());

        if (r.mealType != null) cv.put("meal_type", r.mealType.name());
        if (r.ingredients != null) cv.put("ingredients", formatIngredients(r.ingredients));
        if (r.tags != null) cv.put("tags", r.tags);
        if (r.lastUsed != null) cv.put("last_used", r.lastUsed.toString());
        cv.put("usage_count", r.usageCount);
        cv.put("is_favorite", r.isFavorite ? 1 : 0);

        cv.put("total_calories", r.totalCalories);
        cv.put("total_protein", r.totalProtein);
        cv.put("total_carbs", r.totalCarbs);
        cv.put("total_fat", r.totalFat);

        if (r.id != null) {
            db.update("recipes", cv, "id = ?", new String[]{String.valueOf(r.id)});
        } else {
            long newId = db.insert("recipes", null, cv);
            r.id = newId;
        }
    }

    /**
     * Formatiert Zutaten als String für DB-Speicherung.
     * Format: id|name|amount|unit;id|name|amount|unit;...
     */
    private static String formatIngredients(java.util.List<Recipe.RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ingredients.size(); i++) {
            Recipe.RecipeIngredient ing = ingredients.get(i);
            if (i > 0) sb.append(";");
            sb.append(ing.ingredientId())
              .append("|")
              .append(ing.ingredientName())
              .append("|")
              .append(ing.amount())
              .append("|")
              .append(ing.unit());
        }
        return sb.toString();
    }
}
