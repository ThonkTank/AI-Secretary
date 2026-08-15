package de.thonktank.autosecretary.update.domain;

/** A user-relevant, typed failure at the update boundary. */
public final class UpdateFailure extends Exception {
    public enum Kind {
        NETWORK,
        TIMEOUT,
        HTTP,
        RATE_LIMITED,
        UNTRUSTED_HOST,
        INVALID_RELEASE,
        INCOMPATIBLE_RELEASE,
        CHECKSUM_MISMATCH,
        PACKAGE_MISMATCH,
        SIGNATURE_MISMATCH,
        STORAGE,
        CANCELLED
    }

    private final Kind kind;

    public UpdateFailure(Kind kind, String message) {
        super(message);
        this.kind = requireKind(kind);
    }

    public UpdateFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = requireKind(kind);
    }

    public Kind kind() {
        return kind;
    }

    private static Kind requireKind(Kind kind) {
        if (kind == null) throw new IllegalArgumentException("Update failure kind is required");
        return kind;
    }
}
