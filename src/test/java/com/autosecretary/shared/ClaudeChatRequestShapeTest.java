package com.autosecretary.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Test;

import java.util.List;

/**
 * Protects the wire shape {@link ClaudeMessagesClient#buildChatRequestBody} produces: model,
 * max_tokens, system, a multi-turn message history (PDF document block before text, tool_result
 * matched to its tool_use id), and the tools + thinking passthrough.
 */
public final class ClaudeChatRequestShapeTest extends AutoSecretaryRobolectricTest {

    @Test
    public void buildsMessagesApiWireBody() throws Exception {
        JSONObject userWithPdf = ClaudeChatMessages.userWithAttachment(
                "Analysiere", "auszug.pdf", "application/pdf", new byte[]{1, 2, 3});

        JSONArray assistantContent = new JSONArray()
                .put(new JSONObject().put("type", "thinking").put("thinking", "denke…"))
                .put(new JSONObject().put("type", "tool_use").put("id", "tu_1")
                        .put("name", "get_tasks").put("input", new JSONObject()));
        JSONObject assistant = ClaudeChatMessages.assistant(assistantContent);

        JSONObject toolResults = ClaudeChatMessages.toolResults(
                List.of(new ClaudeChatMessages.ToolResult("tu_1", "{\"tasks\":[]}", false)));

        JSONArray messages = new JSONArray().put(userWithPdf).put(assistant).put(toolResults);
        JSONObject thinking = new JSONObject().put("type", "adaptive").put("display", "summarized");
        JSONArray tools = new JSONArray().put(new JSONObject()
                .put("name", "get_tasks")
                .put("description", "Liefert Tasks")
                .put("input_schema", new JSONObject().put("type", "object")));
        ClaudeChatRequest request = new ClaudeChatRequest(
                "https://api.anthropic.com", "key", "claude-sonnet-5", 8192,
                "system-prompt", messages, tools, thinking);

        JSONObject body = ClaudeMessagesClient.buildChatRequestBody(request);

        assertEquals("claude-sonnet-5", body.getString("model"));
        assertEquals(8192, body.getInt("max_tokens"));
        assertEquals("system-prompt", body.getString("system"));
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"));
        assertTrue("tools present", body.getJSONArray("tools").length() > 0);

        JSONArray wireMessages = body.getJSONArray("messages");
        assertEquals(3, wireMessages.length());

        // First user message: the PDF document block precedes the text block.
        JSONArray firstContent = wireMessages.getJSONObject(0).getJSONArray("content");
        assertEquals("document", firstContent.getJSONObject(0).getString("type"));
        assertEquals("text", firstContent.getJSONObject(1).getString("type"));

        // Tool result is addressed to the assistant's tool_use id.
        JSONArray thirdContent = wireMessages.getJSONObject(2).getJSONArray("content");
        assertEquals("tool_result", thirdContent.getJSONObject(0).getString("type"));
        assertEquals("tu_1", thirdContent.getJSONObject(0).getString("tool_use_id"));
    }
}
