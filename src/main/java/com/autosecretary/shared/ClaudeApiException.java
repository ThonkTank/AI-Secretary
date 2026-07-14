package com.autosecretary.shared;

/**
 * Runtime error raised by {@link ClaudeMessagesClient} for network failures, non-200 responses,
 * or malformed Claude output. Messages are user-facing German strings.
 *
 * <p>{@link #statusCode()} carries the HTTP status when the error came from a non-200 response
 * ({@code 0} otherwise). Callers use it to react to specific failures — e.g. the assistant retries
 * without the {@code thinking} parameter on a {@code 400} whose message mentions thinking.
 */
public class ClaudeApiException extends RuntimeException {
    private final int statusCode;

    public ClaudeApiException(String message) {
        this(message, 0);
    }

    public ClaudeApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ClaudeApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    /** HTTP status code of the failed response, or {@code 0} for non-HTTP errors. */
    public int statusCode() {
        return statusCode;
    }
}
