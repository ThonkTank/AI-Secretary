package com.autosecretary.features.budget.data.api;

import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ParsedStatement;
import com.autosecretary.features.budget.domain.importing.ParsedTransaction;
import com.autosecretary.shared.ClaudeApiException;
import com.autosecretary.shared.ClaudeChatMessages;
import com.autosecretary.shared.ClaudeChatRequest;
import com.autosecretary.shared.ClaudeChatResponse;
import com.autosecretary.shared.ClaudeChatTransport;
import com.autosecretary.shared.ClaudeEndpointStore;
import com.autosecretary.shared.ClaudeMessagesClient;
import com.autosecretary.shared.ClaudeModelStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses Claude's Messages API to extract structured transaction data from PDF bank statements.
 *
 * <h2>High-Level Flow</h2>
 * <ol>
 *   <li>Accept a PDF byte array and a list of valid import categories</li>
 *   <li>Build a system prompt instructing Claude to extract transactions in a specific JSON schema</li>
 *   <li>Send the PDF + prompt via the shared {@link ClaudeChatTransport}</li>
 *   <li>Parse Claude's JSON response into a {@link ParsedStatement}</li>
 * </ol>
 *
 * <p>Transport, endpoint (via {@link ClaudeEndpointStore}) and model (via {@link ClaudeModelStore})
 * are shared with the assistant chat — the model is user-selectable, so this path honours the same
 * configured model and custom endpoint rather than a hardcoded one.
 *
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API Documentation</a>
 */
public class ClaudeStatementApiClient {
    // 4096 tokens comfortably covers typical bank statements (up to ~150 transactions at ~25 tokens each
    // for the compact JSON schema). Raise if statements with >150 transactions produce truncated responses.
    private static final int MAX_TOKENS = 4096;

    private static final String JSON_TYPE = "type";
    private static final String JSON_CATEGORY_ID = "category_id";

    private final ClaudeChatTransport transport;
    private final ClaudeEndpointStore endpointStore;
    private final ClaudeModelStore modelStore;

    public ClaudeStatementApiClient(ClaudeChatTransport transport,
                                    ClaudeEndpointStore endpointStore,
                                    ClaudeModelStore modelStore) {
        this.transport = transport;
        this.endpointStore = endpointStore;
        this.modelStore = modelStore;
    }

    public ParsedStatement parsePdf(String apiKey, byte[] fileBytes, List<ImportCategory> categories) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("Kein Claude API-Key konfiguriert.");
        }
        try {
            String system = buildSystemPrompt(buildCategoryArray(categories));
            JSONArray messages = new JSONArray().put(ClaudeChatMessages.userWithAttachment(
                    "Analysiere den PDF-Kontoauszug und liefere ausschließlich JSON im definierten Schema zurück.",
                    "kontoauszug.pdf", "application/pdf", fileBytes));
            ClaudeChatResponse response = transport.chat(new ClaudeChatRequest(
                    endpointStore.getBaseUrl(), apiKey, modelStore.getModel(), MAX_TOKENS,
                    system, messages, null, null));
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new ApiException("Keine Text-Antwort von Claude");
            }
            return parseStatement(ClaudeMessagesClient.extractJsonFromMarkdown(text.trim()));
        } catch (ClaudeApiException e) {
            throw new ApiException(e.getMessage(), e);
        } catch (JSONException e) {
            throw new ApiException("Claude-Antwort ungültig: " + e.getMessage(), e);
        }
    }

    private JSONArray buildCategoryArray(List<ImportCategory> categories) throws JSONException {
        JSONArray categoryArray = new JSONArray();
        if (categories != null) {
            for (ImportCategory category : categories) {
                JSONObject cat = new JSONObject();
                cat.put(JSON_CATEGORY_ID, category.id());
                cat.put("name", category.name());
                cat.put(JSON_TYPE, category.direction().name());
                categoryArray.put(cat);
            }
        }
        return categoryArray;
    }

    /**
     * Builds the system prompt instructing Claude how to parse bank statements.
     *
     * <p><b>amount_cents sign convention:</b> Claude returns {@code amount_cents} as a positive
     * integer; the sign (income vs. expense) is inferred downstream by the import pipeline.
     */
    private String buildSystemPrompt(JSONArray categoryArray) {
        return "Du extrahierst Banktransaktionen aus Kontoauszügen. "
                + "Antwortformat: JSON Objekt mit Feldern period_start, period_end, transactions. "
                + "transactions ist ein Array von Objekten mit date (YYYY-MM-DD), amount_cents (int), "
                + "payee (string|null), description (string|null), category_id (string|null), hash (string|null). "
                + "Nutze nur gültige category_id aus dieser Liste: "
                + categoryArray
                + " Kein Markdown, keine Kommentare.";
    }

    private ParsedStatement parseStatement(String json) throws JSONException {
        JSONObject payload = new JSONObject(json);
        LocalDate periodStart = parseOptionalDate(payload.optString("period_start", null));
        LocalDate periodEnd = parseOptionalDate(payload.optString("period_end", null));
        return new ParsedStatement(parseTransactionArray(payload), periodStart, periodEnd);
    }

    private List<ParsedTransaction> parseTransactionArray(JSONObject payload) throws JSONException {
        List<ParsedTransaction> transactions = new ArrayList<>();
        JSONArray jsonTransactions = payload.optJSONArray("transactions");
        if (jsonTransactions != null) {
            for (int i = 0; i < jsonTransactions.length(); i++) {
                transactions.add(parseTransaction(jsonTransactions.getJSONObject(i)));
            }
        }
        return transactions;
    }

    private ParsedTransaction parseTransaction(JSONObject tx) throws JSONException {
        String dateStr = tx.getString("date");
        LocalDate bookingDate;
        try {
            bookingDate = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new ApiException("Ungültiges Datum in Claude-Antwort: \"" + dateStr + "\"", e);
        }
        long amountCents = toLong(tx.get("amount_cents"));
        String payee = emptyToNull(tx.optString("payee", null));
        String note = emptyToNull(tx.optString("description", null));
        String categoryId = emptyToNull(tx.optString(JSON_CATEGORY_ID, null));
        String hash = emptyToNull(tx.optString("hash", null));
        return new ParsedTransaction(bookingDate, amountCents, payee, note, categoryId, hash);
    }

    /** Converts a JSON-parsed value to long, tolerating a stringified number as a defensive fallback. */
    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
