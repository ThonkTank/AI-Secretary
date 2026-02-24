package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import entities.MealType;
import entities.Recipe;

public class RecipeParser {

    public static Recipe fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Recipe r = new Recipe();

        r.id = (Long) typed.get("id");
        r.title = (String) typed.get("title");
        r.description = (String) typed.get("description");
        r.instructions = (String) typed.get("instructions");
        r.mealTypes = parseMealTypes((String) typed.get("meal_types"));
        r.prepTimeMinutes = ParseUtils.safeInt(typed.get("prep_time_minutes"), 0);
        r.cookTimeMinutes = ParseUtils.safeInt(typed.get("cook_time_minutes"), 0);
        r.servings = ParseUtils.safeInt(typed.get("servings"), 2);
        r.minServings = ParseUtils.safeInt(typed.get("min_servings"), 1);
        r.maxServings = ParseUtils.safeInt(typed.get("max_servings"), 8);
        r.scalingPrecision = ParseUtils.safeEnum(Recipe.ScalingPrecision.class, (String) typed.get("scaling_precision"), Recipe.ScalingPrecision.ROUGH);
        r.prepEffort = ParseUtils.safeEnum(Recipe.PrepEffort.class, (String) typed.get("prep_effort"), Recipe.PrepEffort.MEDIUM);
        r.ingredients = parseIngredients((String) typed.get("ingredients_data"));
        r.tags = (String) typed.get("tags");
        r.lastUsed = (LocalDate) typed.get("last_used");
        r.usageCount = ParseUtils.safeInt(typed.get("usage_count"), 0);
        r.isFavorite = ParseUtils.safeBoolean(typed.get("is_favorite"), false);
        r.totalCalories = ParseUtils.safeInt(typed.get("total_calories"), 0);
        r.totalProtein = ParseUtils.safeInt(typed.get("total_protein"), 0);
        r.totalCarbs = ParseUtils.safeInt(typed.get("total_carbs"), 0);
        r.totalFat = ParseUtils.safeInt(typed.get("total_fat"), 0);
        r.shelfLifeDays = ParseUtils.safeInt(typed.get("shelf_life_days"), 0);
        r.ratings = parseRatings((String) typed.get("ratings_data"));

        return r;
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
            case "prep_time_minutes", "cook_time_minutes", "servings", "min_servings",
                 "max_servings", "usage_count", "total_calories", "total_protein",
                 "total_carbs", "total_fat", "shelf_life_days" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_favorite" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "last_used" -> ParseUtils.safeLocalDate(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, Recipe r) {
        ContentValues cv = new ContentValues();

        if (r.title != null) cv.put("title", r.title);
        if (r.description != null) cv.put("description", r.description);
        if (r.instructions != null) cv.put("instructions", r.instructions);
        cv.put("meal_types", serializeMealTypes(r.mealTypes));
        cv.put("prep_time_minutes", r.prepTimeMinutes);
        cv.put("cook_time_minutes", r.cookTimeMinutes);
        cv.put("servings", r.servings);
        cv.put("min_servings", r.minServings);
        cv.put("max_servings", r.maxServings);
        if (r.scalingPrecision != null) cv.put("scaling_precision", r.scalingPrecision.name());
        if (r.prepEffort != null) cv.put("prep_effort", r.prepEffort.name());
        cv.put("ingredients_data", serializeIngredients(r.ingredients));
        if (r.tags != null) cv.put("tags", r.tags);
        if (r.lastUsed != null) cv.put("last_used", r.lastUsed.toString());
        cv.put("usage_count", r.usageCount);
        cv.put("is_favorite", r.isFavorite ? 1 : 0);
        cv.put("total_calories", r.totalCalories);
        cv.put("total_protein", r.totalProtein);
        cv.put("total_carbs", r.totalCarbs);
        cv.put("total_fat", r.totalFat);
        cv.put("shelf_life_days", r.shelfLifeDays);
        cv.put("ratings_data", serializeRatings(r.ratings));

        if (r.id != null) {
            db.update("recipes", cv, "id = ?", new String[]{String.valueOf(r.id)});
        } else {
            r.id = db.insert("recipes", null, cv);
        }
    }

    // ============== SERIALISIERUNG ==============

    /**
     * "BREAKFAST,SNACK" → Set<MealType>
     */
    static Set<MealType> parseMealTypes(String raw) {
        Set<MealType> result = EnumSet.noneOf(MealType.class);
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            MealType mt = ParseUtils.safeEnum(MealType.class, part.trim());
            if (mt != null) result.add(mt);
        }
        return result;
    }

    /**
     * Set<MealType> → "BREAKFAST,SNACK"
     */
    static String serializeMealTypes(Set<MealType> types) {
        if (types == null || types.isEmpty()) return "";
        return types.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * "id|name|amount|unit;id|name|amount|unit" → List<RecipeIngredient>
     */
    static List<Recipe.RecipeIngredient> parseIngredients(String raw) {
        List<Recipe.RecipeIngredient> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length >= 4) {
                long id = ParseUtils.safeInt(parts[0], 0);
                String name = parts[1];
                double amount = ParseUtils.safeDouble(parts[2], 0);
                String unit = parts[3];
                result.add(new Recipe.RecipeIngredient(id, name, amount, unit));
            }
        }
        return result;
    }

    /**
     * List<RecipeIngredient> → "id|name|amount|unit;..."
     */
    static String serializeIngredients(List<Recipe.RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";
        return ingredients.stream()
            .map(i -> i.ingredientId() + "|" + i.ingredientName() + "|" + i.amount() + "|" + i.unit())
            .collect(Collectors.joining(";"));
    }

    /**
     * "memberId|rating,memberId|rating" → List<MemberRating>
     */
    static List<Recipe.MemberRating> parseRatings(String raw) {
        List<Recipe.MemberRating> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String entry : raw.split(",")) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length == 2) {
                long memberId = ParseUtils.safeLong(parts[0], 0);
                int rating = ParseUtils.safeInt(parts[1], 3);
                if (memberId > 0) result.add(new Recipe.MemberRating(memberId, rating));
            }
        }
        return result;
    }

    /**
     * List<MemberRating> → "memberId|rating,..."
     */
    static String serializeRatings(List<Recipe.MemberRating> ratings) {
        if (ratings == null || ratings.isEmpty()) return "";
        return ratings.stream()
            .map(r -> r.memberId() + "|" + r.rating())
            .collect(Collectors.joining(","));
    }
}
