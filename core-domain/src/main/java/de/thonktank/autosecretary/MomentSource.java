package de.thonktank.autosecretary;

/** Monotonic-enough wall-clock instant source for durable waits and background wakeups. */
public interface MomentSource {
    long nowEpochMillis();
}
