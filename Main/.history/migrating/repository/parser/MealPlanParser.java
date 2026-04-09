package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import entities.MealPlan;
import entities.MealType;

public class MealPlanParser {

    public static MealPlan fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        MealPlan p = new MealPlan();

        p.id = (Long) typed.get("id");
        p.date = (LocalDate) typed.get("date");
        p.mealType = ParseUtils.safeEnum(MealType.class, (String) typed.get("meal_type"));
        p.recipeId = ParseUtils.safeInt(typed.get("recipe_id"), 0);
        p.plannedServings = ParseUtils.safeInt(typed.get("planned_servings"), 0);
        p.isCompleted = ParseUtils.safeBoolean(typed.get("is_completed"), false);
        p.actualServings = ParseUtils.safeInt(typed.get("actual_servings"), 0);
        p.completedAt = (LocalDateTime) typed.get("completed_at");
        p.itemId = ParseUtils.safeLong(typed.get("item_id"));
        p.recipeTitle = (String) typed.get("recipe_title");
        p.estimatedCalories = ParseUtils.safeInt(typed.get("estimated_calories"), 0);

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
            case "id", "recipe_id", "item_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "planned_servings", "actual_servings", "estimated_calories" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_completed" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "date" -> ParseUtils.safeLocalDate(v.toString());
            case "completed_at" -> ParseUtils.safeLocalDateTime(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, MealPlan p) {
        ContentValues cv = new ContentValues();

        if (p.date != null) cv.put("date", p.date.toString());
        if (p.mealType != null) cv.put("meal_type", p.mealType.name());
        cv.put("recipe_id", p.recipeId);
        cv.put("planned_servings", p.plannedServings);
        cv.put("is_completed", p.isCompleted ? 1 : 0);
        cv.put("actual_servings", p.actualServings);
        if (p.completedAt != null) cv.put("completed_at", p.completedAt.toString());
        if (p.itemId != null) cv.put("item_id", p.itemId);
        if (p.recipeTitle != null) cv.put("recipe_title", p.recipeTitle);
        cv.put("estimated_calories", p.estimatedCalories);

        if (p.id != null) {
            db.update("meal_plans", cv, "id = ?", new String[]{String.valueOf(p.id)});
        } else {
            p.id = db.insert("meal_plans", null, cv);
        }
    }
}
