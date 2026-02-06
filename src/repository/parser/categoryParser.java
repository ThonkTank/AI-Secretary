package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.Category;

public class categoryParser {

    // ============== BUILDER (DB → Java) ==============

    public static Category fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Category cat = new Category();

        cat.id = (Long) typed.get("id");
        cat.name = (String) typed.get("name");
        cat.icon = (String) typed.get("icon");
        cat.color = (String) typed.get("color");
        cat.isIncome = Boolean.TRUE.equals(typed.get("is_income"));
        cat.isBuiltIn = Boolean.TRUE.equals(typed.get("is_built_in"));
        cat.sortOrder = typed.get("sort_order") instanceof Number n ? n.intValue() : 0;
        cat.isActive = Boolean.TRUE.equals(typed.get("is_active"));

        return cat;
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
            case "id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "sort_order" -> (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_income", "is_built_in", "is_active" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Category cat) {
        ContentValues cv = new ContentValues();

        if (cat.name != null) cv.put("name", cat.name);
        if (cat.icon != null) cv.put("icon", cat.icon);
        if (cat.color != null) cv.put("color", cat.color);
        cv.put("is_income", cat.isIncome ? 1 : 0);
        cv.put("is_built_in", cat.isBuiltIn ? 1 : 0);
        cv.put("sort_order", cat.sortOrder);
        cv.put("is_active", cat.isActive ? 1 : 0);

        if (cat.id != null) {
            db.update("categories", cv, "id = ?", new String[]{String.valueOf(cat.id)});
        } else {
            long newId = db.insert("categories", null, cv);
            cat.id = newId;
        }
    }
}
