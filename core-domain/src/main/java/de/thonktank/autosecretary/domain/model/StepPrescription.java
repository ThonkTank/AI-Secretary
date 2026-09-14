package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable amount and rest policy shared by every step representation. */
public final class StepPrescription {
    public final StepAmount amount;
    public final RestTimerPolicy rest;

    public StepPrescription(StepAmount amount, RestTimerPolicy rest) {
        this.amount = StepAmount.requireValid(amount);
        this.rest = rest == null ? RestTimerPolicy.forAmount(this.amount) : rest;
        if (!(this.amount instanceof StepAmount.SetsReps)
                && this.rest.mode != RestTimerPolicy.Mode.OFF)
            throw new IllegalArgumentException("Only set steps may configure a rest timer");
    }

    public static StepPrescription forAmount(StepAmount amount) {
        return new StepPrescription(amount, RestTimerPolicy.forAmount(amount));
    }

    @Override public boolean equals(Object other) {
        return other instanceof StepPrescription && amount.equals(((StepPrescription) other).amount)
                && rest.equals(((StepPrescription) other).rest);
    }

    @Override public int hashCode() { return Objects.hash(amount, rest); }
}
