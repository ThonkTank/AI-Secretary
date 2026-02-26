package com.autosecretary.features.budget.application.importing;

import android.util.Base64;

import com.autosecretary.features.budget.domain.ImportCategory;

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

/**
 * API-Client für Claude Messages API zum Parsing von PDF-Kontoauszügen.
 */
public class ClaudeStatementApiClient {
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 120000;

    public StatementFileParser.ParsedStatement parsePdf(
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
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", buildSystemPrompt(categories));

        JSONArray content = new JSONArray();
        JSONObject promptPart = new JSONObject();
        promptPart.put("type", "text");
        promptPart.put("text", "Analysiere den PDF-Kontoauszug und liefere ausschließlich JSON im definierten Schema zurück.");
        content.put(promptPart);

        JSONObject filePart = new JSONObject();
        filePart.put("type", "document");
        JSONObject source = new JSONObject();
        source.put("type", "base64");
        source.put("media_type", "application/pdf");
        source.put("data", Base64.encodeToString(fileBytes, Base64.NO_WRAP));
        filePart.put("source", source);
        content.put(filePart);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        JSONArray messages = new JSONArray();
        messages.put(userMessage);
        body.put("messages", messages);
        return body;
    }

    private String buildSystemPrompt(List<ImportCategory> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Du extrahierst Banktransaktionen aus Kontoauszügen. ")
                .append("Antwortformat: JSON Objekt mit Feldern period_start, period_end, transactions. ")
                .append("transactions ist ein Array von Objekten mit date (YYYY-MM-DD), amount_cents (int), ")
                .append("payee (string|null), description (string|null), category_id (string|null), hash (string|null). ")
                .append("Nutze nur gültige category_id aus dieser Liste: ");
        if (categories == null || categories.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            for (int i = 0; i < categories.size(); i++) {
                ImportCategory category = categories.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("{")
                        .append("id:").append(category.id)
                        .append(",name:").append(category.name)
                        .append(",type:").append(category.type)
                        .append("}");
            }
            sb.append("]");
        }
        sb.append(" Kein Markdown, keine Kommentare.");
        return sb.toString();
    }

    private StatementFileParser.ParsedStatement parseSuccessResponse(String responseBody) throws JSONException {
        JSONObject root = new JSONObject(responseBody);
        JSONArray contentBlocks = root.optJSONArray("content");
        if (contentBlocks == null || contentBlocks.length() == 0) {
            throw new ApiException("Keine Text-Antwort von Claude");
        }

        String text = null;
        for (int i = 0; i < contentBlocks.length(); i++) {
            JSONObject block = contentBlocks.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                text = block.optString("text", null);
                break;
            }
        }

        if (text == null || text.isBlank()) {
            throw new ApiException("Keine Text-Antwort von Claude");
        }

        JSONObject payload = new JSONObject(extractJsonFromMarkdown(text));
        LocalDate periodStart = parseOptionalDate(payload.optString("period_start", null));
        LocalDate periodEnd = parseOptionalDate(payload.optString("period_end", null));

        List<StatementFileParser.ParsedTransaction> transactions = new ArrayList<>();
        JSONArray jsonTransactions = payload.optJSONArray("transactions");
        if (jsonTransactions != null) {
            for (int i = 0; i < jsonTransactions.length(); i++) {
                JSONObject tx = jsonTransactions.getJSONObject(i);
                transactions.add(parseTransaction(tx));
            }
        }

        return new StatementFileParser.ParsedStatement(transactions, periodStart, periodEnd);
    }

    private StatementFileParser.ParsedTransaction parseTransaction(JSONObject tx) throws JSONException {
        LocalDate date = LocalDate.parse(tx.getString("date"));
        int amountCents = toInt(tx.get("amount_cents"));
        String payee = emptyToNull(tx.optString("payee", null));
        String description = emptyToNull(tx.optString("description", null));
        String categoryId = emptyToNull(tx.optString("category_id", null));
        String hash = emptyToNull(tx.optString("hash", null));
        return new StatementFileParser.ParsedTransaction(date, amountCents, payee, description, categoryId, hash);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
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
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private ApiException parseErrorResponse(int responseCode, String responseBody) {
        String message = responseBody;
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                message = error.optString("message", responseBody);
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

    private String extractJsonFromMarkdown(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
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
