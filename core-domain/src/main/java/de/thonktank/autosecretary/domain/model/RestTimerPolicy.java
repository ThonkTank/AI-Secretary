package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Per-step policy for the pause between recorded exercise sets. */
public final class RestTimerPolicy {
    public enum Mode { INHERIT, CUSTOM, OFF }

    public final Mode mode;
    public final Integer customSeconds;

    private RestTimerPolicy(Mode mode, Integer customSeconds) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (mode == Mode.CUSTOM && (customSeconds == null || customSeconds < 1))
            throw new IllegalArgumentException("Custom rest timer must be positive");
        if (mode != Mode.CUSTOM && customSeconds != null)
            throw new IllegalArgumentException("Only a custom rest timer has seconds");
        this.customSeconds = customSeconds;
    }

    public static RestTimerPolicy inherit() {
        return new RestTimerPolicy(Mode.INHERIT, null);
    }

    public static RestTimerPolicy custom(int seconds) {
        return new RestTimerPolicy(Mode.CUSTOM, seconds);
    }

    public static RestTimerPolicy off() {
        return new RestTimerPolicy(Mode.OFF, null);
    }

    public static RestTimerPolicy forAmount(StepAmount amount) {
        return amount instanceof StepAmount.SetsReps ? inherit() : off();
    }

    public static RestTimerPolicy fromStorage(String mode, Integer seconds) {
        try {
            Mode value = mode == null ? Mode.INHERIT : Mode.valueOf(mode);
            if (value == Mode.CUSTOM) return custom(seconds == null ? 0 : seconds);
            return value == Mode.OFF ? off() : inherit();
        } catch (RuntimeException invalid) {
            return inherit();
        }
    }

    public int effectiveSeconds(int globalDefaultSeconds) {
        if (globalDefaultSeconds < 1)
            throw new IllegalArgumentException("Global rest timer must be positive");
        if (mode == Mode.OFF) return 0;
        return mode == Mode.CUSTOM ? customSeconds : globalDefaultSeconds;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof RestTimerPolicy)) return false;
        RestTimerPolicy value = (RestTimerPolicy) other;
        return mode == value.mode && Objects.equals(customSeconds, value.customSeconds);
    }

    @Override public int hashCode() { return Objects.hash(mode, customSeconds); }
}
