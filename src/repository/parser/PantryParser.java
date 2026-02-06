package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.PantryItem;

public class PantryParser {

    // ============== BUILDER (DB → Java) ==============

    public static PantryItem fromRow(Map<String, Object> row) {
        PantryItem item = new PantryItem();

        item.id = (Long) row.get("id");
        item.ingredientId = (Long) row.get("ingredient_id");
        item.ingredientName = (String) row.get("ingredient_name");
        item.amount = row.get("amount") instanceof Number n ? n.doubleValue() : 0;
        item.unit = (String) row.get("unit");

        item.purchaseDate = (LocalDate) row.get("purchase_date");
        item.expiryDate = (LocalDate) row.get("expiry_date");

        String locationStr = (String) row.get("storage_location");
        if (locationStr != null) {
            item.location = ParseUtils.safeEnum(PantryItem.StorageLocation.class, locationStr);
        }

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
            case "id", "ingredient_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "amount" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "purchase_date", "expiry_date" -> LocalDate.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, PantryItem item) {
        ContentValues cv = new ContentValues();

        if (item.ingredientId != null) cv.put("ingredient_id", item.ingredientId);
        if (item.ingredientName != null) cv.put("ingredient_name", item.ingredientName);
        cv.put("amount", item.amount);
        if (item.unit != null) cv.put("unit", item.unit);

        if (item.purchaseDate != null) cv.put("purchase_date", item.purchaseDate.toString());
        if (item.expiryDate != null) cv.put("expiry_date", item.expiryDate.toString());
        if (item.location != null) cv.put("storage_location", item.location.name());

        if (item.id != null) {
            db.update("pantry_items", cv, "id = ?", new String[]{String.valueOf(item.id)});
        } else {
            long newId = db.insert("pantry_items", null, cv);
            item.id = newId;
        }
    }
}
