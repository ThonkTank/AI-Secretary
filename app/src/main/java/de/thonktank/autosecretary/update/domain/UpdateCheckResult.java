package de.thonktank.autosecretary.update.domain;

/** Explicit result of checking the production update channel. */
public final class UpdateCheckResult {
    private static final UpdateCheckResult CURRENT = new UpdateCheckResult(null);
    private final UpdateInfo available;

    private UpdateCheckResult(UpdateInfo available) {
        this.available = available;
    }

    public static UpdateCheckResult current() {
        return CURRENT;
    }

    public static UpdateCheckResult available(UpdateInfo update) {
        if (update == null) throw new IllegalArgumentException("Available update is required");
        return new UpdateCheckResult(update);
    }

    public boolean isAvailable() {
        return available != null;
    }

    public UpdateInfo availableUpdate() {
        if (available == null) throw new IllegalStateException("No update is available");
        return available;
    }
}
