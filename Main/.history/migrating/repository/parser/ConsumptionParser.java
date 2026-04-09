package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.ConsumptionLog;

public class ConsumptionParser {

    public static ConsumptionLog fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        ConsumptionLog l = new ConsumptionLog();

        l.id = (Long) typed.get("id");
        l.date = (LocalDate) typed.get("date");
        l.itemId = ParseUtils.safeInt(typed.get("item_id"), 0);
        l.memberId = ParseUtils.safeInt(typed.get("member_id"), 0);
        l.recipeId = ParseUtils.safeInt(typed.get("recipe_id"), 0);
        l.servingsConsumed = ParseUtils.safeDouble(typed.get("servings_consumed"), 0);
        l.calories = ParseUtils.safeInt(typed.get("calories"), 0);
        l.protein = ParseUtils.safeInt(typed.get("protein"), 0);
        l.carbs = ParseUtils.safeInt(typed.get("carbs"), 0);
        l.fat = ParseUtils.safeInt(typed.get("fat"), 0);

        return l;
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
            case "id", "item_id", "member_id", "recipe_id" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "servings_consumed" ->
                (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "calories", "protein", "carbs", "fat" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "date" -> ParseUtils.safeLocalDate(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, ConsumptionLog l) {
        ContentValues cv = new ContentValues();

        if (l.date != null) cv.put("date", l.date.toString());
        cv.put("item_id", l.itemId);
        cv.put("member_id", l.memberId);
        cv.put("recipe_id", l.recipeId);
        cv.put("servings_consumed", l.servingsConsumed);
        cv.put("calories", l.calories);
        cv.put("protein", l.protein);
        cv.put("carbs", l.carbs);
        cv.put("fat", l.fat);

        if (l.id != null) {
            db.update("consumption_logs", cv, "id = ?", new String[]{String.valueOf(l.id)});
        } else {
            l.id = db.insert("consumption_logs", null, cv);
        }
    }
}
