package repository.parser;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import entities.Import;

public class importParser {

    // ============== BUILDER (DB → Java) ==============

    public static Import fromRow(Map<String, Object> row) {
        Map<String, Object> typed = convertRow(row);
        Import imp = new Import();

        imp.id = (Long) typed.get("id");
        imp.accountId = (Long) typed.get("account_id");
        imp.fileName = (String) typed.get("file_name");
        imp.fileHash = (String) typed.get("file_hash");
        imp.importDate = (LocalDateTime) typed.get("import_date");

        imp.periodStart = (LocalDate) typed.get("period_start");
        imp.periodEnd = (LocalDate) typed.get("period_end");

        imp.totalTransactions = typed.get("total_transactions") instanceof Number n ? n.intValue() : 0;
        imp.newTransactions = typed.get("new_transactions") instanceof Number n ? n.intValue() : 0;
        imp.autoCategorized = typed.get("auto_categorized") instanceof Number n ? n.intValue() : 0;

        imp.claudeModel = (String) typed.get("claude_model");
        imp.claudePromptTokens = typed.get("claude_prompt_tokens") instanceof Number n ? n.intValue() : null;
        imp.claudeResponseTokens = typed.get("claude_response_tokens") instanceof Number n ? n.intValue() : null;
        imp.processingTimeMs = typed.get("processing_time_ms") instanceof Number n ? n.intValue() : null;

        imp.rawContent = (String) typed.get("raw_content");
        imp.parsedJson = (String) typed.get("parsed_json");

        String statusStr = (String) typed.get("status");
        if (statusStr != null) {
            imp.status = ParseUtils.safeEnum(Import.ImportStatus.class, statusStr, Import.ImportStatus.PENDING);
        }
        imp.errorMessage = (String) typed.get("error_message");

        return imp;
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
            case "id", "account_id" ->
                (v instanceof Number n) ? n.longValue() : Long.parseLong(v.toString());

            // Int-Felder
            case "total_transactions", "new_transactions", "auto_categorized",
                 "claude_prompt_tokens", "claude_response_tokens", "processing_time_ms" ->
                (v instanceof Number n) ? n.intValue() : Integer.parseInt(v.toString());

            // LocalDate-Felder
            case "period_start", "period_end" -> LocalDate.parse(v.toString());

            // LocalDateTime-Felder
            case "import_date" -> LocalDateTime.parse(v.toString());

            // String-Felder
            default -> v.toString();
        };
    }

    // ============== WRITER (Java → DB) ==============

    public static void toRow(SQLiteDatabase db, Import imp) {
        ContentValues cv = new ContentValues();

        if (imp.accountId != null) cv.put("account_id", imp.accountId);
        if (imp.fileName != null) cv.put("file_name", imp.fileName);
        if (imp.fileHash != null) cv.put("file_hash", imp.fileHash);
        if (imp.importDate != null) cv.put("import_date", imp.importDate.toString());

        if (imp.periodStart != null) cv.put("period_start", imp.periodStart.toString());
        if (imp.periodEnd != null) cv.put("period_end", imp.periodEnd.toString());

        cv.put("total_transactions", imp.totalTransactions);
        cv.put("new_transactions", imp.newTransactions);
        cv.put("auto_categorized", imp.autoCategorized);

        if (imp.claudeModel != null) cv.put("claude_model", imp.claudeModel);
        if (imp.claudePromptTokens != null) cv.put("claude_prompt_tokens", imp.claudePromptTokens);
        if (imp.claudeResponseTokens != null) cv.put("claude_response_tokens", imp.claudeResponseTokens);
        if (imp.processingTimeMs != null) cv.put("processing_time_ms", imp.processingTimeMs);

        if (imp.rawContent != null) cv.put("raw_content", imp.rawContent);
        if (imp.parsedJson != null) cv.put("parsed_json", imp.parsedJson);

        if (imp.status != null) cv.put("status", imp.status.name());
        if (imp.errorMessage != null) cv.put("error_message", imp.errorMessage);

        if (imp.id != null) {
            db.update("imports", cv, "id = ?", new String[]{String.valueOf(imp.id)});
        } else {
            long newId = db.insert("imports", null, cv);
            imp.id = newId;
        }
    }
}
