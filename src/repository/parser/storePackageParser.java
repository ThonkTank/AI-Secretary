package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.StorePackage;

public class storePackageParser {

    // ============== BUILDER (DB → Java) ==============

    public static StorePackage fromRow(Map<String, Object> row) {
        StorePackage pkg = new StorePackage();

        pkg.id = (Long) row.get("id");
        pkg.ingredientId = (Long) row.get("ingredient_id");
        pkg.storeName = (String) row.get("store_name");
        pkg.packageSize = row.get("package_size") instanceof Number n ? n.doubleValue() : 0.0;
        pkg.unit = (String) row.get("unit");
        pkg.priceCents = row.get("price_cents") instanceof Number n ? n.intValue() : 0;

        String lastPurchasedStr = (String) row.get("last_purchased");
        if (lastPurchasedStr != null && !lastPurchasedStr.isEmpty()) {
            pkg.lastPurchased = LocalDate.parse(lastPurchasedStr);
        }

        return pkg;
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
            case "package_size" -> (v instanceof Number n) ? n.doubleValue() : Double.parseDouble(v.toString());
            case "price_cents" -> (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, StorePackage pkg) {
        ContentValues cv = new ContentValues();

        if (pkg.ingredientId != null) cv.put("ingredient_id", pkg.ingredientId);
        if (pkg.storeName != null) cv.put("store_name", pkg.storeName);
        cv.put("package_size", pkg.packageSize);
        if (pkg.unit != null) cv.put("unit", pkg.unit);
        cv.put("price_cents", pkg.priceCents);
        if (pkg.lastPurchased != null) cv.put("last_purchased", pkg.lastPurchased.toString());

        if (pkg.id != null) {
            db.update("store_packages", cv, "id = ?", new String[]{String.valueOf(pkg.id)});
        } else {
            long newId = db.insert("store_packages", null, cv);
            pkg.id = newId;
        }
    }
}
