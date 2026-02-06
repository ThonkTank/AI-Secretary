package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.ConsumptionLog;

public class consumptionParser {

    // ============== BUILDER (DB → Java) ==============

    public static ConsumptionLog fromRow(Map<String, Object> row) {
        ConsumptionLog log = new ConsumptionLog();

        log.id = (Long) row.get("id");
        log.date = (LocalDate) row.get("date");
        log.mealPlanId = (Long) row.get("meal_plan_id");
        log.memberId = (Long) row.get("member_id");

        // Rezept-basiert
        log.recipeId = (Long) row.get("recipe_id");
        log.servingsConsumed = row.get("servings_consumed") instanceof Number n ? n.intValue() : 0;

        // Einzelzutat
        log.ingredientId = (Long) row.get("ingredient_id");
        log.amount = row.get("amount") instanceof Number n ? n.doubleValue() : 0;
        log.unit = (String) row.get("unit");

        // Nährwerte
        log.calories = row.get("calories") instanceof Number n ? n.intValue() : 0;
        log.protein = row.get("protein") instanceof Number n ? n.intValue() : 0;
        log.carbs = row.get("carbs") instanceof Number n ? n.intValue() : 0;
        log.fat = row.get("fat") instanceof Number n ? n.intValue() : 0;

        return log;
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
            case "id", "meal_plan_id", "member_id", "recipe_id", "ingredient_id" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "servings_consumed", "calories", "protein", "carbs", "fat" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "amount" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "date" -> LocalDate.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, ConsumptionLog log) {
        ContentValues cv = new ContentValues();

        if (log.date != null) cv.put("date", log.date.toString());
        if (log.mealPlanId != null) cv.put("meal_plan_id", log.mealPlanId);
        if (log.memberId != null) cv.put("member_id", log.memberId);

        if (log.recipeId != null) cv.put("recipe_id", log.recipeId);
        cv.put("servings_consumed", log.servingsConsumed);

        if (log.ingredientId != null) cv.put("ingredient_id", log.ingredientId);
        cv.put("amount", log.amount);
        if (log.unit != null) cv.put("unit", log.unit);

        cv.put("calories", log.calories);
        cv.put("protein", log.protein);
        cv.put("carbs", log.carbs);
        cv.put("fat", log.fat);

        if (log.id != null) {
            db.update("consumption_logs", cv, "id = ?", new String[]{String.valueOf(log.id)});
        } else {
            long newId = db.insert("consumption_logs", null, cv);
            log.id = newId;
        }
    }
}
