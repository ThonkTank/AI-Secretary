package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.PantryItem;

public class PantryParser {

    public static PantryItem fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        PantryItem p = new PantryItem();

        p.id = (Long) typed.get("id");
        p.ingredientId = ParseUtils.safeInt(typed.get("ingredient_id"), 0);
        p.ingredientName = (String) typed.get("ingredient_name");
        p.amount = ParseUtils.safeDouble(typed.get("amount"), 0);
        p.unit = (String) typed.get("unit");
        p.purchaseDate = (LocalDate) typed.get("purchase_date");
        p.expiryDate = (LocalDate) typed.get("expiry_date");
        p.location = ParseUtils.safeEnum(PantryItem.StorageLocation.class, (String) typed.get("location"), PantryItem.StorageLocation.PANTRY);

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
            case "id", "ingredient_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "amount" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "purchase_date", "expiry_date" -> ParseUtils.safeLocalDate(v.toString());
            default -> v.toString();
        };
    }

    public static void toRow(SQLiteDatabase db, PantryItem p) {
        ContentValues cv = new ContentValues();

        cv.put("ingredient_id", p.ingredientId);
        if (p.ingredientName != null) cv.put("ingredient_name", p.ingredientName);
        cv.put("amount", p.amount);
        if (p.unit != null) cv.put("unit", p.unit);
        if (p.purchaseDate != null) cv.put("purchase_date", p.purchaseDate.toString());
        if (p.expiryDate != null) cv.put("expiry_date", p.expiryDate.toString());
        if (p.location != null) cv.put("location", p.location.name());

        if (p.id != null) {
            db.update("pantry_items", cv, "id = ?", new String[]{String.valueOf(p.id)});
        } else {
            p.id = db.insert("pantry_items", null, cv);
        }
    }
}
