package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.IngredientInstance;

public class IngredientInstanceParser {

    public static IngredientInstance fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        IngredientInstance i = new IngredientInstance();

        i.id = (Long) typed.get("id");
        i.ingredientId = ParseUtils.safeInt(typed.get("ingredient_id"), 0);
        i.amount = ParseUtils.safeDouble(typed.get("amount"), 0);
        i.purchaseDate = (LocalDate) typed.get("purchase_date");
        i.isPurchased = ParseUtils.safeBoolean(typed.get("is_purchased"), false);

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
            case "amount" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "purchase_date" -> ParseUtils.safeLocalDate(v.toString());
            case "is_purchased" -> (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    /**
     * Schreibt IngredientInstance in die korrekte Tabelle.
     * purchaseDate != null → pantry_items, sonst → shopping_list_items.
     */
    public static void toRow(SQLiteDatabase db, IngredientInstance i) {
        boolean isPantry = i.purchaseDate != null;
        String tableName = isPantry ? "pantry_items" : "shopping_list_items";

        ContentValues cv = new ContentValues();
        cv.put("ingredient_id", i.ingredientId);
        cv.put("amount", i.amount);

        if (isPantry) {
            cv.put("purchase_date", i.purchaseDate.toString());
        } else {
            cv.put("is_purchased", i.isPurchased ? 1 : 0);
        }

        if (i.id != null) {
            db.update(tableName, cv, "id = ?", new String[]{String.valueOf(i.id)});
        } else {
            i.id = db.insert(tableName, null, cv);
        }
    }
}
