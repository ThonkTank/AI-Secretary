package com.autosecretary.shared;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One Messages API request. {@code messages} and {@code tools} are already in Anthropic wire shape
 * (build them with {@link ClaudeChatMessages}); {@code thinking} is passed through verbatim so
 * callers can select the thinking mode a given model accepts.
 *
 * @param baseUrl   endpoint base (see {@link ClaudeEndpointStore}); null/blank → default endpoint
 * @param apiKey    Anthropic API key
 * @param model     model id
 * @param maxTokens response token budget (must exceed the thinking budget when thinking is enabled)
 * @param system    system prompt string (may be null)
 * @param messages  full conversation, wire shape
 * @param tools     tool definitions {@code [{name, description, input_schema}]}, or null
 * @param thinking  thinking parameter object, or null to omit
 */
public record ClaudeChatRequest(String baseUrl,
                                String apiKey,
                                String model,
                                int maxTokens,
                                String system,
                                JSONArray messages,
                                JSONArray tools,
                                JSONObject thinking) {
}
