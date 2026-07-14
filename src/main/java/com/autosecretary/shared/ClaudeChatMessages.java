package com.autosecretary.shared;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builders for Anthropic Messages API wire-shape message objects, so feature code never hand-writes
 * content-block JSON. Every method returns a {@code {"role":…, "content":[…]}} object ready to append
 * to a {@link ClaudeChatRequest#messages()} array.
 */
public final class ClaudeChatMessages {
    private static final String PDF_MIME = "application/pdf";

    private ClaudeChatMessages() {
    }

    /** A user message with a single text block. */
    public static JSONObject userText(String text) {
        try {
            return message("user", new JSONArray().put(textBlock(text)));
        } catch (JSONException e) {
            throw new ClaudeApiException("Nachricht konnte nicht erstellt werden: " + e.getMessage(), e);
        }
    }

    /**
     * A user message carrying an attachment. PDFs become a base64 {@code document} block placed
     * before the text; text-like files (txt/md) are decoded UTF-8 and appended as a second text
     * block wrapped as {@code Datei "<name>":\n<content>}.
     */
    public static JSONObject userWithAttachment(String text, String displayName,
                                                String mimeType, byte[] bytes) {
        try {
            JSONArray content = new JSONArray();
            if (isPdf(mimeType, displayName)) {
                content.put(documentBlock(bytes));
                content.put(textBlock(text));
            } else {
                content.put(textBlock(text));
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                content.put(textBlock("Datei \"" + displayName + "\":\n" + decoded));
            }
            return message("user", content);
        } catch (JSONException e) {
            throw new ClaudeApiException("Anhang konnte nicht erstellt werden: " + e.getMessage(), e);
        }
    }

    /** Echoes an assistant turn verbatim (its {@code content} array from a prior response). */
    public static JSONObject assistant(JSONArray rawContent) {
        return message("assistant", rawContent);
    }

    /** A user message bundling all tool results for the tool_use blocks of the previous assistant turn. */
    public static JSONObject toolResults(List<ToolResult> results) {
        try {
            JSONArray content = new JSONArray();
            for (ToolResult result : results) {
                JSONObject block = new JSONObject();
                block.put("type", "tool_result");
                block.put("tool_use_id", result.toolUseId());
                block.put("content", result.content());
                if (result.isError()) {
                    block.put("is_error", true);
                }
                content.put(block);
            }
            return message("user", content);
        } catch (JSONException e) {
            throw new ClaudeApiException("Werkzeug-Ergebnis konnte nicht erstellt werden: " + e.getMessage(), e);
        }
    }

    private static boolean isPdf(String mimeType, String displayName) {
        if (PDF_MIME.equalsIgnoreCase(mimeType)) {
            return true;
        }
        return displayName != null && displayName.toLowerCase().endsWith(".pdf");
    }

    private static JSONObject textBlock(String text) throws JSONException {
        return new JSONObject().put("type", "text").put("text", text);
    }

    private static JSONObject documentBlock(byte[] bytes) throws JSONException {
        JSONObject source = new JSONObject();
        source.put("type", "base64");
        source.put("media_type", PDF_MIME);
        source.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        return new JSONObject().put("type", "document").put("source", source);
    }

    private static JSONObject message(String role, JSONArray content) {
        try {
            return new JSONObject().put("role", role).put("content", content);
        } catch (JSONException e) {
            throw new ClaudeApiException("Nachricht konnte nicht erstellt werden: " + e.getMessage(), e);
        }
    }

    /** One tool result: the text the model receives back for a given {@code tool_use_id}. */
    public record ToolResult(String toolUseId, String content, boolean isError) {
    }
}
