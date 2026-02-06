package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import entities.ShoppingListItem;

public class ShoppingListParser {

    // ============== BUILDER (DB → Java) ==============

    public static ShoppingListItem fromRow(Map<String, Object> row) {
        ShoppingListItem item = new ShoppingListItem();

        item.id = (Long) row.get("id");
        item.weekKey = (String) row.get("week_key");
        item.ingredientId = (Long) row.get("ingredient_id");
        item.ingredientName = (String) row.get("ingredient_name");
        item.amount = row.get("amount") instanceof Number n ? n.doubleValue() : 0;
        item.unit = (String) row.get("unit");

        item.isPurchased = Boolean.TRUE.equals(row.get("is_purchased"));
        item.purchasedAt = (LocalDateTime) row.get("purchased_at");
        item.actualPriceCents = row.get("actual_price_cents") instanceof Number n ? n.intValue() : 0;

        item.sourceRecipeId = (Long) row.get("source_recipe_id");
        item.foodGroupLabel = (String) row.get("food_group_label");

        // Phase 3C: Single-Store-Optimierung
        item.neededAmount = row.get("needed_amount") instanceof Number n ? n.doubleValue() : 0;
        item.excessAmount = row.get("excess_amount") instanceof Number n ? n.doubleValue() : 0;
        item.suggestedStore = (String) row.get("suggested_store");

        return item;
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
            case "id", "ingredient_id", "source_recipe_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "amount", "needed_amount", "excess_amount" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "actual_price_cents" -> (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_purchased" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "purchased_at" -> LocalDateTime.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, ShoppingListItem item) {
        ContentValues cv = new ContentValues();

        if (item.weekKey != null) cv.put("week_key", item.weekKey);
        if (item.ingredientId != null) cv.put("ingredient_id", item.ingredientId);
        if (item.ingredientName != null) cv.put("ingredient_name", item.ingredientName);
        cv.put("amount", item.amount);
        if (item.unit != null) cv.put("unit", item.unit);

        cv.put("is_purchased", item.isPurchased ? 1 : 0);
        if (item.purchasedAt != null) cv.put("purchased_at", item.purchasedAt.toString());
        cv.put("actual_price_cents", item.actualPriceCents);

        if (item.sourceRecipeId != null) cv.put("source_recipe_id", item.sourceRecipeId);
        if (item.foodGroupLabel != null) cv.put("food_group_label", item.foodGroupLabel);

        // Phase 3C: Single-Store-Optimierung
        cv.put("needed_amount", item.neededAmount);
        cv.put("excess_amount", item.excessAmount);
        if (item.suggestedStore != null) cv.put("suggested_store", item.suggestedStore);

        if (item.id != null) {
            db.update("shopping_list_items", cv, "id = ?", new String[]{String.valueOf(item.id)});
        } else {
            long newId = db.insert("shopping_list_items", null, cv);
            item.id = newId;
        }
    }
}
