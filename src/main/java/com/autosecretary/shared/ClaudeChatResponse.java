package com.autosecretary.shared;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Parsed result of one Messages API call, exposing the three views callers need plus the verbatim
 * assistant content for lossless echo back into the next request.
 *
 * @param stopReason           API {@code stop_reason} ("end_turn", "tool_use", "max_tokens", "pause_turn", …)
 * @param rawAssistantContent  the response {@code content} array unchanged — re-send it (incl. thinking
 *                             blocks with their signatures and tool_use blocks) as the assistant turn
 *                             when continuing a tool loop
 * @param text                 all text blocks joined ("" if none)
 * @param thinkingText         all thinking blocks joined ("" if none / thinking omitted)
 * @param toolUses             tool_use blocks the model emitted (empty when stopReason != "tool_use")
 */
public record ClaudeChatResponse(String stopReason,
                                 JSONArray rawAssistantContent,
                                 String text,
                                 String thinkingText,
                                 List<ToolUse> toolUses) {

    /** A single tool invocation requested by the model. */
    public record ToolUse(String id, String name, JSONObject input) {
    }
}
