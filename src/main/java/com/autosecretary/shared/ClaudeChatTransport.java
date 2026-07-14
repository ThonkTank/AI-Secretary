package com.autosecretary.shared;

import java.util.function.Consumer;

/**
 * Feature-neutral transport for a single Anthropic Messages API round-trip that supports the full
 * chat surface: a multi-message conversation, tools, and extended thinking. Implemented by
 * {@link ClaudeMessagesClient}; expressed as an interface so callers (and their tests) can script
 * responses without a live HTTP connection.
 */
public interface ClaudeChatTransport {
    /**
     * Performs one Messages API call.
     *
     * @throws ClaudeApiException on missing key, network failure, non-200 response, or malformed output
     */
    ClaudeChatResponse chat(ClaudeChatRequest request);

    /**
     * Same as {@link #chat(ClaudeChatRequest)}, but reports the extended-thinking text as it streams
     * in: {@code onThinking} is called with the accumulated thinking of the current round each time
     * more arrives, so callers can surface the model's reasoning live. Scripted test transports that
     * only implement {@link #chat(ClaudeChatRequest)} inherit this no-streaming default.
     */
    default ClaudeChatResponse chat(ClaudeChatRequest request, Consumer<String> onThinking) {
        return chat(request);
    }
}
