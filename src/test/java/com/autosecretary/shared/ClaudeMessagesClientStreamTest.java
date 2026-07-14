package com.autosecretary.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Locks in {@link ClaudeMessagesClient#parseSseStream}: the streamed Messages API events must
 * reassemble into the same {@code content[]}/text/thinking/tool_use shape a buffered reply has,
 * preserve the thinking {@code signature} for the echo-back, concatenate {@code input_json_delta}
 * fragments into the tool input, and report thinking live via the callback.
 */
public final class ClaudeMessagesClientStreamTest extends AutoSecretaryRobolectricTest {

    /** Invariant: a thinking + text + tool_use round reassembles fully and streams thinking live. */
    @Test
    public void reassemblesThinkingTextAndToolUseFromStream() throws Exception {
        String stream = sse(
                blockStart(0, block("thinking").put("thinking", "")),
                delta(0, deltaObj("thinking_delta").put("thinking", "Ich prüfe ")),
                delta(0, deltaObj("thinking_delta").put("thinking", "die Tasks.")),
                delta(0, deltaObj("signature_delta").put("signature", "SIG==")),
                blockStop(0),
                blockStart(1, block("text").put("text", "")),
                delta(1, deltaObj("text_delta").put("text", "Einen Moment.")),
                blockStop(1),
                blockStart(2, block("tool_use").put("id", "tu_1").put("name", "get_tasks").put("input", new JSONObject())),
                delta(2, deltaObj("input_json_delta").put("partial_json", "{\"limit\":")),
                delta(2, deltaObj("input_json_delta").put("partial_json", "5}")),
                blockStop(2),
                messageDelta("tool_use"));

        List<String> thinkingUpdates = new ArrayList<>();
        ClaudeChatResponse response = parse(stream, thinkingUpdates);

        assertEquals("tool_use", response.stopReason());
        assertEquals("Ich prüfe die Tasks.", response.thinkingText());
        assertEquals("Einen Moment.", response.text());

        assertEquals(1, response.toolUses().size());
        assertEquals("get_tasks", response.toolUses().get(0).name());
        assertEquals("tu_1", response.toolUses().get(0).id());
        assertEquals(5, response.toolUses().get(0).input().getInt("limit"));

        // Raw content echoes back all three blocks, with the thinking signature intact.
        JSONArray raw = response.rawAssistantContent();
        assertEquals(3, raw.length());
        assertEquals("SIG==", raw.getJSONObject(0).getString("signature"));
        assertEquals("Ich prüfe die Tasks.", raw.getJSONObject(0).getString("thinking"));
        assertEquals(5, raw.getJSONObject(2).getJSONObject("input").getInt("limit"));

        // Thinking is reported live and cumulatively as it streams.
        assertEquals(List.of("Ich prüfe ", "Ich prüfe die Tasks."), thinkingUpdates);
    }

    /** Invariant: a tool_use block with no input_json_delta yields an empty-object input, not a crash. */
    @Test
    public void toolUseWithoutInputDeltasYieldsEmptyInput() throws Exception {
        String stream = sse(
                blockStart(0, block("tool_use").put("id", "tu_2").put("name", "get_recipes").put("input", new JSONObject())),
                blockStop(0),
                messageDelta("tool_use"));

        ClaudeChatResponse response = parse(stream, new ArrayList<>());

        assertEquals(1, response.toolUses().size());
        assertEquals(0, response.toolUses().get(0).input().length());
    }

    /** Invariant: an SSE error event surfaces as a ClaudeApiException carrying the message. */
    @Test
    public void errorEventThrows() throws JSONException {
        String stream = sse(new JSONObject().put("type", "error")
                .put("error", new JSONObject().put("type", "overloaded_error").put("message", "overloaded")));

        ClaudeApiException thrown = assertThrows(ClaudeApiException.class,
                () -> parse(stream, new ArrayList<>()));
        assertTrue(thrown.getMessage().contains("overloaded"));
    }

    private static ClaudeChatResponse parse(String stream, List<String> thinkingUpdates)
            throws IOException, JSONException {
        try (BufferedReader reader = new BufferedReader(new StringReader(stream))) {
            return ClaudeMessagesClient.parseSseStream(reader, thinkingUpdates::add);
        }
    }

    // ---- SSE builders ------------------------------------------------------

    private static String sse(JSONObject... events) {
        StringBuilder sb = new StringBuilder();
        for (JSONObject event : events) {
            sb.append("event: ").append(event.optString("type")).append("\n");
            sb.append("data: ").append(event).append("\n\n");
        }
        return sb.toString();
    }

    private static JSONObject block(String type) throws JSONException {
        return new JSONObject().put("type", type);
    }

    private static JSONObject deltaObj(String type) throws JSONException {
        return new JSONObject().put("type", type);
    }

    private static JSONObject blockStart(int index, JSONObject contentBlock) throws JSONException {
        return new JSONObject().put("type", "content_block_start").put("index", index)
                .put("content_block", contentBlock);
    }

    private static JSONObject delta(int index, JSONObject delta) throws JSONException {
        return new JSONObject().put("type", "content_block_delta").put("index", index).put("delta", delta);
    }

    private static JSONObject blockStop(int index) throws JSONException {
        return new JSONObject().put("type", "content_block_stop").put("index", index);
    }

    private static JSONObject messageDelta(String stopReason) throws JSONException {
        return new JSONObject().put("type", "message_delta")
                .put("delta", new JSONObject().put("stop_reason", stopReason));
    }
}
