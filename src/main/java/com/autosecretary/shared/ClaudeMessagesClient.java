package com.autosecretary.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Feature-neutral transport for the Anthropic Messages API. Exposes a full chat surface via
 * {@link #chat(ClaudeChatRequest)} (multi-message conversation, tools, extended thinking).
 * Centralises the HTTP boilerplate, error mapping (German user-facing messages) and markdown-fence
 * stripping so feature clients only build their request content and parse their own payloads.
 *
 * <p>The base URL is configurable (see {@link ClaudeEndpointStore}) so the same client can talk to
 * either {@code api.anthropic.com} (with an API key) or a self-hosted proxy speaking the same API.
 */
public final class ClaudeMessagesClient implements ClaudeChatTransport {
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 120000;

    private static final String JSON_TYPE = "type";
    private static final String JSON_TEXT = "text";
    private static final String JSON_CONTENT = "content";

    /**
     * Performs one full Messages API call (conversation + optional tools + optional thinking) and
     * returns the parsed response. See {@link ClaudeChatMessages} for building {@code request.messages}.
     *
     * @throws ClaudeApiException on missing key, network failure, non-200 response, or malformed output
     */
    @Override
    public ClaudeChatResponse chat(ClaudeChatRequest request) {
        return chat(request, text -> { });
    }

    @Override
    public ClaudeChatResponse chat(ClaudeChatRequest request, java.util.function.Consumer<String> onThinking) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new ClaudeApiException("Kein Claude API-Key konfiguriert.");
        }

        HttpURLConnection connection = null;
        try {
            JSONObject requestBody = buildChatRequestBody(request);
            connection = (HttpURLConnection) new URL(resolveUrl(request.baseUrl())).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", request.apiKey());
            connection.setRequestProperty("anthropic-version", API_VERSION);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw mapError(responseCode, readResponseBody(connection, responseCode));
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return parseSseStream(reader, onThinking);
            }
        } catch (IOException e) {
            throw new ClaudeApiException("Netzwerkfehler bei der Claude-Anfrage: " + e.getMessage(), e);
        } catch (JSONException e) {
            throw new ClaudeApiException("Claude-Antwort ungültig: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static JSONObject buildChatRequestBody(ClaudeChatRequest request) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        // Stream so a long generation (large lists → many tool_use blocks) keeps the socket fed with
        // incremental events instead of returning nothing until done and tripping the read timeout.
        body.put("stream", true);
        if (request.system() != null) {
            body.put("system", request.system());
        }
        body.put("messages", request.messages());
        if (request.tools() != null && request.tools().length() > 0) {
            body.put("tools", request.tools());
        }
        if (request.thinking() != null) {
            body.put("thinking", request.thinking());
        }
        return body;
    }

    /**
     * Reassembles a streamed Messages API response (SSE) into the same {@code content[]} shape a
     * non-streamed reply has, then extracts it via {@link #buildResponse}. Blocks are rebuilt from
     * {@code content_block_start} + the {@code *_delta} events; {@code tool_use} input arrives as
     * {@code input_json_delta} fragments concatenated and parsed at {@code content_block_stop}, and a
     * thinking block's {@code signature} is accumulated too so the raw content echoes back valid on the
     * next round. {@code onThinking} receives the running thinking text of the current block as it grows.
     */
    static ClaudeChatResponse parseSseStream(BufferedReader reader, java.util.function.Consumer<String> onThinking)
            throws IOException, JSONException {
        JSONArray content = new JSONArray();
        Map<Integer, StringBuilder> toolInput = new HashMap<>();
        String stopReason = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue; // "event:" lines and blank separators carry no payload we need
            }
            String data = line.substring("data:".length()).trim();
            if (data.isEmpty()) {
                continue;
            }
            JSONObject event = new JSONObject(data);
            switch (event.optString(JSON_TYPE)) {
                case "content_block_start" -> {
                    int index = event.getInt("index");
                    JSONObject block = event.getJSONObject("content_block");
                    content.put(index, block);
                    if ("tool_use".equals(block.optString(JSON_TYPE))) {
                        toolInput.put(index, new StringBuilder());
                    }
                }
                case "content_block_delta" -> applyBlockDelta(content, toolInput, event, onThinking);
                case "content_block_stop" -> {
                    int index = event.getInt("index");
                    StringBuilder buffer = toolInput.get(index);
                    if (buffer != null) {
                        String json = buffer.toString().trim();
                        content.getJSONObject(index)
                                .put("input", json.isEmpty() ? new JSONObject() : new JSONObject(json));
                    }
                }
                case "message_delta" -> {
                    JSONObject delta = event.optJSONObject("delta");
                    if (delta != null && !delta.isNull("stop_reason")) {
                        stopReason = delta.optString("stop_reason", null);
                    }
                }
                case "error" -> {
                    JSONObject error = event.optJSONObject("error");
                    throw new ClaudeApiException("Claude-Streamingfehler: "
                            + (error != null ? error.optString("message", data) : data));
                }
                default -> { /* message_start, ping, message_stop: nothing to accumulate */ }
            }
        }
        return buildResponse(stopReason, content);
    }

    private static void applyBlockDelta(JSONArray content, Map<Integer, StringBuilder> toolInput,
                                        JSONObject event, java.util.function.Consumer<String> onThinking)
            throws JSONException {
        int index = event.getInt("index");
        JSONObject delta = event.getJSONObject("delta");
        JSONObject block = content.getJSONObject(index);
        switch (delta.optString(JSON_TYPE)) {
            case "text_delta" -> block.put(JSON_TEXT, block.optString(JSON_TEXT, "") + delta.optString(JSON_TEXT, ""));
            case "thinking_delta" -> {
                String thinking = block.optString("thinking", "") + delta.optString("thinking", "");
                block.put("thinking", thinking);
                onThinking.accept(thinking);
            }
            case "signature_delta" ->
                    block.put("signature", block.optString("signature", "") + delta.optString("signature", ""));
            case "input_json_delta" -> {
                StringBuilder buffer = toolInput.get(index);
                if (buffer != null) {
                    buffer.append(delta.optString("partial_json", ""));
                }
            }
            default -> { /* other delta types are not surfaced */ }
        }
    }

    private static ClaudeChatResponse buildResponse(String stopReason, JSONArray content) {
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        List<ClaudeChatResponse.ToolUse> toolUses = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            switch (block.optString(JSON_TYPE)) {
                case "text" -> append(text, block.optString(JSON_TEXT, ""));
                case "thinking" -> append(thinking, block.optString("thinking", ""));
                case "tool_use" -> toolUses.add(new ClaudeChatResponse.ToolUse(
                        block.optString("id", ""),
                        block.optString("name", ""),
                        block.optJSONObject("input") != null ? block.optJSONObject("input") : new JSONObject()));
                default -> { /* redacted_thinking etc.: kept in rawContent for the echo, not surfaced */ }
            }
        }
        return new ClaudeChatResponse(stopReason, content, text.toString(), thinking.toString(), toolUses);
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(part);
        }
    }

    static String resolveUrl(String baseUrl) {
        String base = (baseUrl == null || baseUrl.isBlank())
                ? ClaudeEndpointStore.DEFAULT_BASE_URL
                : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + MESSAGES_PATH;
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

    private ClaudeApiException mapError(int responseCode, String responseBody) {
        String message = responseBody;
        try {
            JSONObject error = new JSONObject(responseBody).optJSONObject("error");
            if (error != null) {
                message = error.optString("message", responseBody);
            }
        } catch (JSONException ignored) {
            // fall back to raw body
        }
        return switch (responseCode) {
            case 401 -> new ClaudeApiException("Ungültiger API-Key (401)", responseCode);
            case 429 -> new ClaudeApiException("API-Limit erreicht. Bitte später erneut versuchen (429)", responseCode);
            case 500, 502, 503 -> new ClaudeApiException("Claude-Server nicht erreichbar (" + responseCode + ")", responseCode);
            default -> new ClaudeApiException("API-Fehler " + responseCode + ": " + message, responseCode);
        };
    }

    /**
     * Strips optional markdown code fences (```json … ```) from Claude output and returns the raw
     * inner content. Handles both multi-line fences (language on the first line) and inline fences.
     */
    public static String extractJsonFromMarkdown(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int openingFenceEnd = 3;
        int newlineAfterOpening = trimmed.indexOf('\n');
        if (newlineAfterOpening > 0) {
            openingFenceEnd = newlineAfterOpening + 1;
        }
        String content = trimmed.substring(openingFenceEnd);
        int closingFenceIndex = content.lastIndexOf("```");
        return closingFenceIndex < 0 ? content.trim() : content.substring(0, closingFenceIndex).trim();
    }
}
