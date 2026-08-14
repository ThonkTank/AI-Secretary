package com.autosecretary.application.update;

public record UpdateFailure(Kind kind, String detail, boolean retryable) {
    public enum Kind {
        NETWORK,
        RATE_LIMITED,
        INVALID_RELEASE,
        SECURITY_REJECTED,
        DOWNLOAD_FAILED,
        STORAGE,
        PERMISSION,
        INTERNAL
    }

    public UpdateFailure {
        if (kind == null) kind = Kind.INTERNAL;
        if (detail == null || detail.isBlank()) detail = "Update konnte nicht abgeschlossen werden";
    }
}
