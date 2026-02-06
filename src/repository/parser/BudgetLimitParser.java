package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import entities.BudgetLimit;

public class BudgetLimitParser {

    // ============== BUILDER (DB → Java) ==============

    public static BudgetLimit fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        BudgetLimit bl = new BudgetLimit();

        bl.id = (Long) typed.get("id");
        bl.categoryId = (Long) typed.get("category_id");
        bl.yearMonth = (String) typed.get("year_month");
        bl.limitCents = typed.get("limit_cents") instanceof Number n ? n.intValue() : 0;
        bl.spentCents = typed.get("spent_cents") instanceof Number n ? n.intValue() : 0;
        bl.rolloverCents = typed.get("rollover_cents") instanceof Number n ? n.intValue() : 0;
        bl.notes = (String) typed.get("notes");

        return bl;
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
            case "id", "category_id" -> (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());
            case "limit_cents", "spent_cents", "rollover_cents" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, BudgetLimit bl) {
        ContentValues cv = new ContentValues();

        if (bl.categoryId != null) cv.put("category_id", bl.categoryId);
        if (bl.yearMonth != null) cv.put("year_month", bl.yearMonth);
        cv.put("limit_cents", bl.limitCents);
        cv.put("spent_cents", bl.spentCents);
        cv.put("rollover_cents", bl.rolloverCents);
        if (bl.notes != null) cv.put("notes", bl.notes);

        if (bl.id != null) {
            db.update("budget_limits", cv, "id = ?", new String[]{String.valueOf(bl.id)});
        } else {
            long newId = db.insert("budget_limits", null, cv);
            bl.id = newId;
        }
    }
}
