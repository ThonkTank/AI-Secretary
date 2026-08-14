package com.autosecretary.platform.update;

/** Precise transport evidence used by the update repository's public failure mapping. */
final class HttpTransportException extends Exception {
    enum Kind { RATE_LIMITED, INVALID_RESPONSE }
    private final Kind kind;

    HttpTransportException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    Kind kind() { return kind; }
}
