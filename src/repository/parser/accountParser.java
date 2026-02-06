package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import entities.Account;

public class accountParser {

    // ============== BUILDER (DB → Java) ==============

    public static Account fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Account acc = new Account();

        acc.id = (Long) typed.get("id");
        acc.name = (String) typed.get("name");
        String typeStr = (String) typed.get("type");
        if (typeStr != null) {
            acc.type = ParseUtils.safeEnum(Account.AccountType.class, typeStr);
        }
        acc.currentBalanceCents = typed.get("current_balance_cents") instanceof Number n ? n.intValue() : 0;
        acc.initialBalanceCents = typed.get("initial_balance_cents") instanceof Number n ? n.intValue() : 0;
        acc.currency = (String) typed.get("currency");
        acc.institution = (String) typed.get("institution");
        acc.accountNumber = (String) typed.get("account_number");
        acc.color = (String) typed.get("color");
        acc.icon = (String) typed.get("icon");
        acc.isActive = Boolean.TRUE.equals(typed.get("is_active"));
        acc.includeInTotal = Boolean.TRUE.equals(typed.get("include_in_total"));
        acc.created = (LocalDate) typed.get("created");

        return acc;
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
            case "current_balance_cents", "initial_balance_cents" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());
            case "is_active", "include_in_total" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());
            case "created" -> LocalDate.parse(v.toString());
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Account acc) {
        ContentValues cv = new ContentValues();

        if (acc.name != null) cv.put("name", acc.name);
        if (acc.type != null) cv.put("type", acc.type.name());
        cv.put("current_balance_cents", acc.currentBalanceCents);
        cv.put("initial_balance_cents", acc.initialBalanceCents);
        if (acc.currency != null) cv.put("currency", acc.currency);
        if (acc.institution != null) cv.put("institution", acc.institution);
        if (acc.accountNumber != null) cv.put("account_number", acc.accountNumber);
        if (acc.color != null) cv.put("color", acc.color);
        if (acc.icon != null) cv.put("icon", acc.icon);
        cv.put("is_active", acc.isActive ? 1 : 0);
        cv.put("include_in_total", acc.includeInTotal ? 1 : 0);
        if (acc.created != null) cv.put("created", acc.created.toString());

        if (acc.id != null) {
            db.update("accounts", cv, "id = ?", new String[]{String.valueOf(acc.id)});
        } else {
            long newId = db.insert("accounts", null, cv);
            acc.id = newId;
        }
    }
}
