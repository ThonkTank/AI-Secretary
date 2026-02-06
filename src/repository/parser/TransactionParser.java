package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import entities.Transaction;

public class transactionParser {

    // ============== BUILDER (DB → Java) ==============

    public static Transaction fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Transaction tx = new Transaction();

        // Basic
        tx.id = (Long) typed.get("id");
        tx.accountId = (Long) typed.get("account_id");
        tx.amountCents = typed.get("amount_cents") instanceof Number n ? n.intValue() : 0;
        tx.transactionDate = (LocalDate) typed.get("transaction_date");
        tx.valueDate = (LocalDate) typed.get("value_date");
        tx.categoryId = (Long) typed.get("category_id");
        tx.subcategory = (String) typed.get("subcategory");
        tx.description = (String) typed.get("description");
        tx.payee = (String) typed.get("payee");

        // Typ-Flags
        tx.isIncome = Boolean.TRUE.equals(typed.get("is_income"));
        tx.isRecurring = Boolean.TRUE.equals(typed.get("is_recurring"));

        // Recurring-Felder
        String repType = (String) typed.get("repetition_type");
        if (repType != null) {
            tx.repetitionType = ParseUtils.safeEnum(Transaction.RecurringType.class, repType);
        }
        tx.repetitionValue = typed.get("repetition_value") instanceof Number n ? n.intValue() : 0;
        String repUnit = (String) typed.get("repetition_unit");
        if (repUnit != null) {
            tx.repetitionUnit = ParseUtils.safeEnum(Transaction.RepUnits.class, repUnit);
        }
        String dow = (String) typed.get("day_of_week");
        if (dow != null) {
            tx.dayOfWeek = ParseUtils.safeDayOfWeek(dow);
        }
        tx.nextDue = (LocalDate) typed.get("next_due");
        tx.lastOccurrence = (LocalDate) typed.get("last_occurrence");

        // Varianz-Tracking
        tx.amountMinCents = typed.get("amount_min_cents") instanceof Number n ? n.intValue() : null;
        tx.amountMaxCents = typed.get("amount_max_cents") instanceof Number n ? n.intValue() : null;
        tx.amountAvgCents = typed.get("amount_avg_cents") instanceof Number n ? n.intValue() : null;
        tx.occurrenceCount = typed.get("occurrence_count") instanceof Number n ? n.intValue() : 0;

        // Verknüpfungen
        tx.parentRecurringId = (Long) typed.get("parent_recurring_id");
        tx.linkedTransactionId = (Long) typed.get("linked_transaction_id");
        tx.importId = (Long) typed.get("import_id");
        tx.importHash = (String) typed.get("import_hash");
        tx.isConfirmed = Boolean.TRUE.equals(typed.get("is_confirmed"));
        tx.isPredicted = Boolean.TRUE.equals(typed.get("is_predicted"));

        tx.created = (LocalDateTime) typed.get("created");

        return tx;
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
            // Long-Felder
            case "id", "account_id", "category_id", "parent_recurring_id", "linked_transaction_id", "import_id" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());

            // Int-Felder
            case "amount_cents", "repetition_value", "amount_min_cents", "amount_max_cents",
                 "amount_avg_cents", "occurrence_count" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());

            // Boolean-Felder
            case "is_income", "is_recurring", "is_confirmed", "is_predicted" ->
                (v instanceof Number n) ? n.intValue() != 0 : "1".equals(v.toString());

            // LocalDate-Felder
            case "transaction_date", "value_date", "next_due", "last_occurrence" ->
                LocalDate.parse(v.toString());

            // LocalDateTime-Felder
            case "created" -> LocalDateTime.parse(v.toString());

            // String-Felder
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Transaction tx) {
        ContentValues cv = new ContentValues();

        // Basic
        if (tx.accountId != null) cv.put("account_id", tx.accountId);
        cv.put("amount_cents", tx.amountCents);
        if (tx.transactionDate != null) cv.put("transaction_date", tx.transactionDate.toString());
        if (tx.valueDate != null) cv.put("value_date", tx.valueDate.toString());
        if (tx.categoryId != null) cv.put("category_id", tx.categoryId);
        if (tx.subcategory != null) cv.put("subcategory", tx.subcategory);
        if (tx.description != null) cv.put("description", tx.description);
        if (tx.payee != null) cv.put("payee", tx.payee);

        // Typ-Flags
        cv.put("is_income", tx.isIncome ? 1 : 0);
        cv.put("is_recurring", tx.isRecurring ? 1 : 0);

        // Recurring-Felder
        if (tx.repetitionType != null) cv.put("repetition_type", tx.repetitionType.name());
        cv.put("repetition_value", tx.repetitionValue);
        if (tx.repetitionUnit != null) cv.put("repetition_unit", tx.repetitionUnit.name());
        if (tx.dayOfWeek != null) cv.put("day_of_week", tx.dayOfWeek.name());
        if (tx.nextDue != null) cv.put("next_due", tx.nextDue.toString());
        if (tx.lastOccurrence != null) cv.put("last_occurrence", tx.lastOccurrence.toString());

        // Varianz-Tracking
        if (tx.amountMinCents != null) cv.put("amount_min_cents", tx.amountMinCents);
        if (tx.amountMaxCents != null) cv.put("amount_max_cents", tx.amountMaxCents);
        if (tx.amountAvgCents != null) cv.put("amount_avg_cents", tx.amountAvgCents);
        cv.put("occurrence_count", tx.occurrenceCount);

        // Verknüpfungen
        if (tx.parentRecurringId != null) cv.put("parent_recurring_id", tx.parentRecurringId);
        if (tx.linkedTransactionId != null) cv.put("linked_transaction_id", tx.linkedTransactionId);
        if (tx.importId != null) cv.put("import_id", tx.importId);
        if (tx.importHash != null) cv.put("import_hash", tx.importHash);
        cv.put("is_confirmed", tx.isConfirmed ? 1 : 0);
        cv.put("is_predicted", tx.isPredicted ? 1 : 0);

        if (tx.created != null) cv.put("created", tx.created.toString());

        if (tx.id != null) {
            db.update("transactions", cv, "id = ?", new String[]{String.valueOf(tx.id)});
        } else {
            long newId = db.insert("transactions", null, cv);
            tx.id = newId;
        }
    }
}
