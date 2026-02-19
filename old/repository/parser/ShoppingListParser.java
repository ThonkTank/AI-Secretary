package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.ShoppingListItem;

public class ShoppingListParser {

    public static ShoppingListItem fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        ShoppingListItem i = new ShoppingListItem();

        i.id = (Long) typed.get("id");
        i.ingredientId = ParseUtils.safeInt(typed.get("ingredient_id"), 0);
        i.ingredientName = (String) typed.get("ingredient_name");
        i.amount = ParseUtils.safeDouble(typed.get("amount"), 0);
        i.neededAmount = ParseUtils.safeDouble(typed.get("needed_amount"), 0);
        i.excessAmount = ParseUtils.safeDouble(typed.get("excess_amount"), 0);
        i.unit = (String) typed.get("unit");
        i.foodGroupLabel = (String) typed.get("food_group_label");
        i.suggestedStore = (String) typed.get("suggested_store");
        i.isPurchased = ParseUtils.safeBoolean(typed.get("is_purchased"), false);
        i.periodKey = (String) typed.get("period_key");
        i.estimatedPriceCents = ParseUtils.safeInt(typed.get("estimated_price_cents"), 0);

        return i;
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
            case "id", "ingredient_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "amount", "needed_amount", "excess_amount" ->
                (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "estimated_price_cents" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_purchased" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, ShoppingListItem i) {
        ContentValues cv = new ContentValues();

        cv.put("ingredient_id", i.ingredientId);
        if (i.ingredientName != null) cv.put("ingredient_name", i.ingredientName);
        cv.put("amount", i.amount);
        cv.put("needed_amount", i.neededAmount);
        cv.put("excess_amount", i.excessAmount);
        if (i.unit != null) cv.put("unit", i.unit);
        if (i.foodGroupLabel != null) cv.put("food_group_label", i.foodGroupLabel);
        if (i.suggestedStore != null) cv.put("suggested_store", i.suggestedStore);
        cv.put("is_purchased", i.isPurchased ? 1 : 0);
        if (i.periodKey != null) cv.put("period_key", i.periodKey);
        cv.put("estimated_price_cents", i.estimatedPriceCents);

        if (i.id != null) {
            db.update("shopping_list_items", cv, "id = ?", new String[]{String.valueOf(i.id)});
        } else {
            i.id = db.insert("shopping_list_items", null, cv);
        }
    }
}
