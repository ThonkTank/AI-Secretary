package com.autosecretary.features.budget.data.api;

import android.util.Base64;

import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ParsedStatement;
import com.autosecretary.features.budget.domain.importing.ParsedTransaction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uses Claude's Messages API to extract structured transaction data from PDF bank statements.
 *
 * <h2>High-Level Flow</h2>
 * <ol>
 *   <li>Accept a PDF byte array and a list of valid import categories</li>
 *   <li>Build a system prompt instructing Claude to extract transactions in a specific JSON schema</li>
 *   <li>Send the PDF and prompt to Claude's Messages API (via HTTPS)</li>
 *   <li>Parse Claude's JSON response into a {@link ParsedStatement}</li>
 * </ol>
 *
 * <h2>API Requirements</h2>
 * Requires a valid Anthropic API key passed to {@link #parsePdf(String, byte[], List)}.
 * The key should be configured by the user in app settings and retrieved via {@link ClaudeApiKeyStore}.
 *
 * <h2>Response Parsing</h2>
 * Claude's response is expected to be a JSON object with fields: period_start, period_end, transactions.
 * If Claude wraps the JSON in markdown code fences, they are stripped automatically.
 *
 * @see <a href="https://docs.anthropic.com/en/api/messages">Anthropic Messages API Documentation</a>
 */
public class ClaudeStatementApiClient {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    // Claude Sonnet 4 provides good accuracy-to-cost ratio for financial document parsing.
    // See: https://docs.anthropic.com/en/docs/about-claude/models/latest
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int CONNECT_TIMEOUT = 30000;
    // Timeouts account for network latency and Claude API processing time.
    // - Connect (30s): network handshake
    // - Read (120s): Claude processing PDFs can be slow (PDF parsing, extraction, JSON generation)
    // Adjust if you see frequent timeouts in logs.
    private static final int READ_TIMEOUT = 120000;

    // JSON field names for Anthropic Messages API requests and responses.
    // These constants prevent typos and ensure consistency across request/response handling.
    // See: https://docs.anthropic.com/en/api/messages
    private static final String JSON_MODEL = "model";
    private static final String JSON_MAX_TOKENS = "max_tokens";
    private static final String JSON_SYSTEM = "system";
    private static final String JSON_TYPE = "type";
    private static final String JSON_TEXT = "text";
    private static final String JSON_DOCUMENT = "document";
    private static final String JSON_SOURCE = "source";
    private static final String JSON_BASE64 = "base64";
    private static final String JSON_MEDIA_TYPE = "media_type";
    private static final String JSON_DATA = "data";
    private static final String JSON_CONTENT = "content";
    private static final String JSON_ROLE = "role";
    private static final String JSON_USER = "user";
    private static final String JSON_MESSAGES = "messages";
    private static final String JSON_ERROR = "error";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_PERIOD_START = "period_start";
    private static final String JSON_PERIOD_END = "period_end";
    private static final String JSON_TRANSACTIONS = "transactions";
    private static final String JSON_DATE = "date";
    private static final String JSON_AMOUNT_CENTS = "amount_cents";
    private static final String JSON_PAYEE = "payee";
    private static final String JSON_DESCRIPTION = "description";
    private static final String JSON_CATEGORY_ID = "category_id";
    private static final String JSON_HASH = "hash";
    private static final String CONTENT_TYPE_TEXT = "text";
    private static final String CONTENT_TYPE_DOCUMENT = "document";
    private static final String MEDIA_TYPE_PDF = "application/pdf";

    public ParsedStatement parsePdf(
            String apiKey,
            byte[] fileBytes,
            List<ImportCategory> categories
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("Kein Claude API-Key konfiguriert.");
        }

        HttpURLConnection connection = null;
        try {
            JSONObject requestBody = buildRequestBody(fileBytes, categories);
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", API_VERSION);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);
            if (responseCode != 200) {
                throw parseErrorResponse(responseCode, responseBody);
            }
            return parseSuccessResponse(responseBody);
        } catch (IOException e) {
            throw new ApiException("Netzwerkfehler beim PDF-Import: " + e.getMessage(), e);
        } catch (JSONException e) {
            throw new ApiException("Claude-Antwort ungültig: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject buildRequestBody(byte[] fileBytes, List<ImportCategory> categories) throws JSONException {
        JSONObject body = new JSONObject();
        body.put(JSON_MODEL, MODEL);
        body.put(JSON_MAX_TOKENS, MAX_TOKENS);
        body.put(JSON_SYSTEM, buildSystemPrompt(categories));

        JSONArray content = new JSONArray();
        JSONObject promptPart = new JSONObject();
        promptPart.put(JSON_TYPE, CONTENT_TYPE_TEXT);
        promptPart.put(JSON_TEXT, "Analysiere den PDF-Kontoauszug und liefere ausschließlich JSON im definierten Schema zurück.");
        content.put(promptPart);

        JSONObject filePart = new JSONObject();
        filePart.put(JSON_TYPE, CONTENT_TYPE_DOCUMENT);
        JSONObject source = new JSONObject();
        source.put(JSON_TYPE, JSON_BASE64);
        source.put(JSON_MEDIA_TYPE, MEDIA_TYPE_PDF);
        source.put(JSON_DATA, Base64.encodeToString(fileBytes, Base64.NO_WRAP));
        filePart.put(JSON_SOURCE, source);
        content.put(filePart);

        JSONObject userMessage = new JSONObject();
        userMessage.put(JSON_ROLE, JSON_USER);
        userMessage.put(JSON_CONTENT, content);

        JSONArray messages = new JSONArray();
        messages.put(userMessage);
        body.put(JSON_MESSAGES, messages);
        return body;
    }

    /**
     * Builds the system prompt that instructs Claude how to parse bank statements.
     *
     * The prompt tells Claude to:
     * <ul>
     *   <li>Extract transactions from the PDF statement</li>
     *   <li>Return a JSON object with period_start, period_end, and a transactions array</li>
     *   <li>Use only valid category_id values from the provided list</li>
     *   <li>Return plain JSON with no markdown formatting or explanation</li>
     * </ul>
     *
     * Categories are passed as JSON for clarity and consistency with the output schema.
     *
     * @param categories List of valid categories the user has configured; Claude will only assign these IDs
     * @return A German-language system prompt (matches the expected schema and language of statements)
     */
    private String buildSystemPrompt(List<ImportCategory> categories) throws JSONException {
        JSONArray categoryArray = buildCategoryArray(categories);
        return buildSystemPromptText(categoryArray);
    }

    private JSONArray buildCategoryArray(List<ImportCategory> categories) throws JSONException {
        JSONArray categoryArray = new JSONArray();
        if (categories != null) {
            for (ImportCategory category : categories) {
                JSONObject cat = new JSONObject();
                cat.put("id", category.id());
                cat.put("name", category.name());
                cat.put("type", category.direction().name());
                categoryArray.put(cat);
            }
        }
        return categoryArray;
    }

    private String buildSystemPromptText(JSONArray categoryArray) {
        return "Du extrahierst Banktransaktionen aus Kontoauszügen. "
                + "Antwortformat: JSON Objekt mit Feldern period_start, period_end, transactions. "
                + "transactions ist ein Array von Objekten mit date (YYYY-MM-DD), amount_cents (int), "
                + "payee (string|null), description (string|null), category_id (string|null), hash (string|null). "
                + "Nutze nur gültige category_id aus dieser Liste: "
                + categoryArray
                + " Kein Markdown, keine Kommentare.";
    }

    private String extractTextFromContentBlocks(JSONArray contentBlocks) throws JSONException {
        for (int i = 0; i < contentBlocks.length(); i++) {
            JSONObject block = contentBlocks.getJSONObject(i);
            if (CONTENT_TYPE_TEXT.equals(block.optString(JSON_TYPE))) {
                String text = block.optString(JSON_TEXT, null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private ParsedStatement parseSuccessResponse(String responseBody) throws JSONException {
        JSONObject root = new JSONObject(responseBody);
        JSONArray contentBlocks = root.optJSONArray(JSON_CONTENT);
        if (contentBlocks == null || contentBlocks.length() == 0) {
            throw new ApiException("Keine Text-Antwort von Claude");
        }

        String text = extractTextFromContentBlocks(contentBlocks);
        if (text == null || text.isBlank()) {
            throw new ApiException("Keine Text-Antwort von Claude");
        }

        JSONObject payload = new JSONObject(extractJsonFromMarkdown(text));
        LocalDate periodStart = parseOptionalDate(payload.optString(JSON_PERIOD_START, null));
        LocalDate periodEnd = parseOptionalDate(payload.optString(JSON_PERIOD_END, null));

        List<ParsedTransaction> transactions = parseTransactionArray(payload);

        return new ParsedStatement(transactions, periodStart, periodEnd);
    }

    private List<ParsedTransaction> parseTransactionArray(JSONObject payload) throws JSONException {
        List<ParsedTransaction> transactions = new ArrayList<>();
        JSONArray jsonTransactions = payload.optJSONArray(JSON_TRANSACTIONS);
        if (jsonTransactions != null) {
            for (int i = 0; i < jsonTransactions.length(); i++) {
                JSONObject tx = jsonTransactions.getJSONObject(i);
                transactions.add(parseTransaction(tx));
            }
        }
        return transactions;
    }

    private ParsedTransaction parseTransaction(JSONObject tx) throws JSONException {
        String dateStr = tx.getString(JSON_DATE);
        LocalDate bookingDate;
        try {
            bookingDate = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new ApiException("Ungültiges Datum in Claude-Antwort: \"" + dateStr + "\"", e);
        }
        long amountCents = toLong(tx.get(JSON_AMOUNT_CENTS));
        String payee = emptyToNull(tx.optString(JSON_PAYEE, null));
        String note = emptyToNull(tx.optString(JSON_DESCRIPTION, null));
        String categoryId = emptyToNull(tx.optString(JSON_CATEGORY_ID, null));
        String hash = emptyToNull(tx.optString(JSON_HASH, null));
        return new ParsedTransaction(bookingDate, amountCents, payee, note, categoryId, hash);
    }

    /**
     * Converts a JSON-parsed value to long.
     * Handles both numeric types (expected) and strings (fallback for defensive parsing).
     * The fallback covers cases where Claude or the JSON library returns a stringified number.
     */
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

    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        var inputStream = (responseCode >= 200 && responseCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private ApiException parseErrorResponse(int responseCode, String responseBody) {
        String message = responseBody;
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject error = root.optJSONObject(JSON_ERROR);
            if (error != null) {
                message = error.optString(JSON_MESSAGE, responseBody);
            }
        } catch (JSONException ignored) {
            // fallback to raw body
        }

        return switch (responseCode) {
            case 401 -> new ApiException("Ungültiger API-Key (401)");
            case 429 -> new ApiException("API-Limit erreicht. Bitte später erneut versuchen (429)");
            case 500, 502, 503 -> new ApiException("Claude-Server nicht erreichbar (" + responseCode + ")");
            default -> new ApiException("API-Fehler " + responseCode + ": " + message);
        };
    }

    /**
     * Claude may wrap JSON responses in markdown code fences (```json ... ```).
     * This method strips those fences to get the raw JSON.
     *
     * Examples:
     * <ul>
     *   <li>Input: `{"key": "value"}` → Returns: `{"key": "value"}`</li>
     *   <li>Input: ` ```json\n{"key": "value"}\n``` ` → Returns: `{"key": "value"}`</li>
     * </ul>
     */
    private String extractJsonFromMarkdown(String text) {
        String trimmed = text == null ? "" : text.trim();
        return removeFences(trimmed).trim();
    }

    /**
     * Removes markdown code block fences (```...```) from text.
     * Assumes at most one fence block; only handles the first one found.
     */
    private String removeFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return text;
        }
        String afterFirst = text.substring(firstLineEnd + 1);
        int lastFence = afterFirst.lastIndexOf("```");
        if (lastFence < 0) {
            return afterFirst;
        }
        return afterFirst.substring(0, lastFence);
    }

    private static String emptyToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
