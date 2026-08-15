package de.thonktank.autosecretary.update.application;

/** Millisecond clock used for update throttling and postponement. */
public interface UpdateClock {
    long nowMillis();
}
