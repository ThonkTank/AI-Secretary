package controller.budgetTab;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

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
import java.util.concurrent.Executors;

import data.ClaudePrompts;
import entities.Category;

/**
 * HTTP-Client für Claude Messages API.
 * Parst Kontoauszüge (PDF/CSV) und extrahiert Transaktionen.
 */
public class ClaudeApiClient {

    private static final String TAG = "ClaudeApiClient";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 120000;  // PDF-Verarbeitung kann langsam sein

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ============== CALLBACK INTERFACE ==============

    public interface ApiCallback {
        void onSuccess(ParsedStatementResult result);
        void onError(String errorMessage);
    }

    // ============== RESULT RECORDS ==============

    public record ParsedStatementResult(
        String periodStart,
        String periodEnd,
        List<ParsedTransaction> transactions,
        int promptTokens,
        int responseTokens,
        long processingTimeMs
    ) {}

    public record ParsedTransaction(
        LocalDate date,
        int amountCents,
        String payee,
        String description,
        Long categoryId,
        String hash
    ) {}

    // ============== PUBLIC API ==============

    /**
     * Parst einen Kontoauszug (PDF oder Text) asynchron.
     *
     * @param apiKey Claude API Key
     * @param fileBytes Datei-Inhalt als Bytes
     * @param mimeType z.B. "application/pdf" oder "text/csv"
     * @param categories Liste aller Kategorien für dynamischen Prompt
     * @param callback Callback für Ergebnis/Fehler
     */
    public void parseStatementAsync(String apiKey, byte[] fileBytes, String mimeType,
                                    List<Category> categories, ApiCallback callback) {
        long startTime = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ParsedStatementResult result = parseStatementSync(apiKey, fileBytes, mimeType, categories, startTime);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                Log.e(TAG, "API error: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                mainHandler.post(() -> callback.onError("Unerwarteter Fehler: " + e.getMessage()));
            }
        });
    }

    // ============== INTERNAL ==============

    private ParsedStatementResult parseStatementSync(String apiKey, byte[] fileBytes, String mimeType,
                                                      List<Category> categories, long startTime) throws ApiException {
        HttpURLConnection conn = null;
        try {
            // Request Body aufbauen
            JSONObject requestBody = buildRequestBody(fileBytes, mimeType, categories);

            // HTTP Connection
            conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            // Headers
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", API_VERSION);

            // Request senden
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }

            // Response lesen
            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn, responseCode);

            // Fehler behandeln
            if (responseCode != 200) {
                throw parseErrorResponse(responseCode, responseBody);
            }

            // Erfolgreiche Response parsen
            return parseSuccessResponse(responseBody, startTime);

        } catch (IOException e) {
            throw new ApiException("Netzwerkfehler: " + e.getMessage());
        } catch (JSONException e) {
            throw new ApiException("JSON-Fehler: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JSONObject buildRequestBody(byte[] fileBytes, String mimeType, List<Category> categories) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", ClaudePrompts.buildSystemPrompt(categories));

        // User message mit Datei-Inhalt
        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");

        JSONArray content = new JSONArray();

        // Text-Prompt
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", ClaudePrompts.USER_PROMPT);
        content.put(textPart);

        // Datei-Inhalt (als Document für PDF, als Text für CSV)
        if (mimeType.equals("application/pdf")) {
            JSONObject docPart = new JSONObject();
            docPart.put("type", "document");
            JSONObject source = new JSONObject();
            source.put("type", "base64");
            source.put("media_type", "application/pdf");
            source.put("data", Base64.encodeToString(fileBytes, Base64.NO_WRAP));
            docPart.put("source", source);
            content.put(docPart);
        } else {
            // CSV/Text: Als Text anhängen
            JSONObject textFilePart = new JSONObject();
            textFilePart.put("type", "text");
            textFilePart.put("text", "Kontoauszug-Inhalt:\n```\n" + new String(fileBytes, StandardCharsets.UTF_8) + "\n```");
            content.put(textFilePart);
        }

        userMessage.put("content", content);
        messages.put(userMessage);
        body.put("messages", messages);

        return body;
    }

    private String readResponse(HttpURLConnection conn, int responseCode) throws IOException {
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private ApiException parseErrorResponse(int responseCode, String responseBody) {
        String message;
        try {
            JSONObject error = new JSONObject(responseBody);
            JSONObject errorObj = error.optJSONObject("error");
            if (errorObj != null) {
                message = errorObj.optString("message", "Unbekannter Fehler");
            } else {
                message = responseBody;
            }
        } catch (JSONException e) {
            message = responseBody;
        }

        return switch (responseCode) {
            case 401 -> new ApiException("Ungültiger API-Key (401)");
            case 429 -> new ApiException("API-Limit erreicht. Bitte warten (429)");
            case 500, 502, 503 -> new ApiException("Claude-Server nicht erreichbar (" + responseCode + ")");
            default -> new ApiException("API-Fehler " + responseCode + ": " + message);
        };
    }

    private ParsedStatementResult parseSuccessResponse(String responseBody, long startTime) throws ApiException {
        try {
            JSONObject response = new JSONObject(responseBody);

            // Token-Nutzung
            JSONObject usage = response.optJSONObject("usage");
            int promptTokens = usage != null ? usage.optInt("input_tokens", 0) : 0;
            int responseTokens = usage != null ? usage.optInt("output_tokens", 0) : 0;

            // Content extrahieren
            JSONArray contentArray = response.getJSONArray("content");
            if (contentArray.length() == 0) {
                throw new ApiException("Keine Antwort von Claude erhalten");
            }

            String textContent = null;
            for (int i = 0; i < contentArray.length(); i++) {
                JSONObject block = contentArray.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    textContent = block.getString("text");
                    break;
                }
            }

            if (textContent == null) {
                throw new ApiException("Keine Text-Antwort von Claude");
            }

            // JSON aus Markdown-Code-Block extrahieren falls vorhanden
            textContent = extractJsonFromMarkdown(textContent);

            // JSON parsen
            JSONObject parsed = new JSONObject(textContent);

            String periodStart = parsed.optString("period_start", null);
            String periodEnd = parsed.optString("period_end", null);

            // Transaktionen parsen
            List<ParsedTransaction> transactions = new ArrayList<>();
            JSONArray txArray = parsed.optJSONArray("transactions");
            if (txArray != null) {
                for (int i = 0; i < txArray.length(); i++) {
                    JSONObject tx = txArray.getJSONObject(i);
                    try {
                        ParsedTransaction parsed_tx = parseTransaction(tx);
                        transactions.add(parsed_tx);
                    } catch (Exception e) {
                        Log.w(TAG, "Transaktion " + i + " übersprungen: " + e.getMessage());
                    }
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            return new ParsedStatementResult(periodStart, periodEnd, transactions, promptTokens, responseTokens, processingTime);

        } catch (JSONException e) {
            throw new ApiException("Antwort konnte nicht geparst werden: " + e.getMessage());
        }
    }

    private String extractJsonFromMarkdown(String text) {
        // Entfernt ```json ... ``` falls vorhanden
        text = text.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    private ParsedTransaction parseTransaction(JSONObject tx) throws JSONException {
        // Datum
        String dateStr = tx.getString("date");
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new JSONException("Ungültiges Datum: " + dateStr);
        }

        // Betrag (kann als int oder double kommen)
        int amountCents;
        Object amountObj = tx.get("amount_cents");
        if (amountObj instanceof Number n) {
            amountCents = n.intValue();
        } else {
            amountCents = Integer.parseInt(amountObj.toString());
        }

        // Kategorie-ID (optional, kann null sein)
        Long categoryId = null;
        if (tx.has("category_id") && !tx.isNull("category_id")) {
            categoryId = tx.getLong("category_id");
        }

        return new ParsedTransaction(
            date,
            amountCents,
            tx.optString("payee", null),
            tx.optString("description", null),
            categoryId,
            tx.optString("hash", null)
        );
    }

    // ============== EXCEPTION ==============

    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }
}
