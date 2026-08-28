package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Durable learning state; it contains no opaque model parameters. */
public final class TrainingAssistantState {
    public enum Status { DISABLED, CALIBRATING, ACTIVE, PAUSED }

    public final Status status;
    public final int eligibleObservations;
    public final int readyStreak;
    public final int hardStreak;

    public TrainingAssistantState(Status status, int eligibleObservations,
                                  int readyStreak, int hardStreak) {
        if (status == null || eligibleObservations < 0 || readyStreak < 0 || hardStreak < 0)
            throw new IllegalArgumentException("Invalid training assistant state");
        this.status = status;
        this.eligibleObservations = eligibleObservations;
        this.readyStreak = readyStreak;
        this.hardStreak = hardStreak;
    }

    public static TrainingAssistantState disabled() {
        return new TrainingAssistantState(Status.DISABLED, 0, 0, 0);
    }

    public static TrainingAssistantState calibrating() {
        return new TrainingAssistantState(Status.CALIBRATING, 0, 0, 0);
    }

    public static TrainingAssistantState restore(String status, int observations,
                                                  int ready, int hard) {
        try { return new TrainingAssistantState(Status.valueOf(status), observations, ready, hard); }
        catch (RuntimeException invalid) { return disabled(); }
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingAssistantState)) return false;
        TrainingAssistantState value = (TrainingAssistantState) other;
        return status == value.status && eligibleObservations == value.eligibleObservations
                && readyStreak == value.readyStreak && hardStreak == value.hardStreak;
    }

    @Override public int hashCode() {
        return Objects.hash(status, eligibleObservations, readyStreak, hardStreak);
    }
}
