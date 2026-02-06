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

    // ============== BUILDER (DB → Java) ==============

    public static MealPlan fromRow(Map<String, Object> row) {
        MealPlan mp = new MealPlan();

        mp.id = (Long) row.get("id");
        mp.date = (LocalDate) row.get("date");

        String mealTypeStr = (String) row.get("meal_type");
        if (mealTypeStr != null) {
            mp.mealType = ParseUtils.safeEnum(MealType.class, mealTypeStr);
        }

        mp.recipeId = (Long) row.get("recipe_id");
        mp.plannedServings = row.get("planned_servings") instanceof Number n ? n.intValue() : 2;

        mp.isCompleted = Boolean.TRUE.equals(row.get("is_completed"));
        mp.actualServings = row.get("actual_servings") instanceof Number n ? n.intValue() : 0;
        mp.completedAt = (LocalDateTime) row.get("completed_at");

        mp.recipeTitle = (String) row.get("recipe_title");
        mp.estimatedCalories = row.get("estimated_calories") instanceof Number n ? n.intValue() : 0;

        return mp;
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
            case "id", "recipe_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "planned_servings", "actual_servings", "estimated_calories" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_completed" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "date" -> LocalDate.parse(v.toString());
            case "completed_at" -> LocalDateTime.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, MealPlan mp) {
        ContentValues cv = new ContentValues();

        if (mp.date != null) cv.put("date", mp.date.toString());
        if (mp.mealType != null) cv.put("meal_type", mp.mealType.name());
        if (mp.recipeId != null) cv.put("recipe_id", mp.recipeId);
        cv.put("planned_servings", mp.plannedServings);

        cv.put("is_completed", mp.isCompleted ? 1 : 0);
        cv.put("actual_servings", mp.actualServings);
        if (mp.completedAt != null) cv.put("completed_at", mp.completedAt.toString());

        if (mp.recipeTitle != null) cv.put("recipe_title", mp.recipeTitle);
        cv.put("estimated_calories", mp.estimatedCalories);

        if (mp.id != null) {
            db.update("meal_plans", cv, "id = ?", new String[]{String.valueOf(mp.id)});
        } else {
            long newId = db.insert("meal_plans", null, cv);
            mp.id = newId;
        }
    }
}
