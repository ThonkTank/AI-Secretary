package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Delay attached to an edge between two flow steps. */
public final class FlowDelayPolicy {
    public enum Mode { FIXED, REMEMBER_LAST }

    public static final long MAX_DELAY_MILLIS = 30L * 24L * 60L * 60L * 1_000L;

    public final Mode mode;
    public final long defaultDelayMillis;
    public final Long lastUsedDelayMillis;

    public FlowDelayPolicy(Mode mode, long defaultDelayMillis, Long lastUsedDelayMillis) {
        this.mode = Objects.requireNonNull(mode, "mode");
        requireDelay(defaultDelayMillis);
        if (lastUsedDelayMillis != null) requireDelay(lastUsedDelayMillis);
        this.defaultDelayMillis = defaultDelayMillis;
        this.lastUsedDelayMillis = lastUsedDelayMillis;
    }

    public static FlowDelayPolicy fixed(long delayMillis) {
        return new FlowDelayPolicy(Mode.FIXED, delayMillis, null);
    }

    public static FlowDelayPolicy rememberLast(long defaultDelayMillis) {
        return new FlowDelayPolicy(Mode.REMEMBER_LAST, defaultDelayMillis, null);
    }

    public long proposedDelayMillis() {
        return mode == Mode.REMEMBER_LAST && lastUsedDelayMillis != null
                ? lastUsedDelayMillis : defaultDelayMillis;
    }

    public long choose(Long enteredDelayMillis) {
        long chosen = enteredDelayMillis == null ? proposedDelayMillis() : enteredDelayMillis;
        requireDelay(chosen);
        return chosen;
    }

    public FlowDelayPolicy remember(long chosenDelayMillis) {
        requireDelay(chosenDelayMillis);
        return mode == Mode.REMEMBER_LAST
                ? new FlowDelayPolicy(mode, defaultDelayMillis, chosenDelayMillis) : this;
    }

    private static void requireDelay(long value) {
        if (value < 0L || value > MAX_DELAY_MILLIS)
            throw new IllegalArgumentException("Flow delay must be between zero and 30 days");
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof FlowDelayPolicy)) return false;
        FlowDelayPolicy value = (FlowDelayPolicy) other;
        return mode == value.mode && defaultDelayMillis == value.defaultDelayMillis
                && Objects.equals(lastUsedDelayMillis, value.lastUsedDelayMillis);
    }

    @Override public int hashCode() {
        return Objects.hash(mode, defaultDelayMillis, lastUsedDelayMillis);
    }
}
