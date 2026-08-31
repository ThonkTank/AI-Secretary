package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** The complete immutable execution prescription shared by every step representation. */
public final class StepPrescription {
    public final StepAmount amount;
    public final RestTimerPolicy rest;
    public final TrainingPrescription training;

    public StepPrescription(StepAmount amount, RestTimerPolicy rest,
                            TrainingPrescription training) {
        this.amount = StepAmount.requireValid(amount);
        this.rest = rest == null ? RestTimerPolicy.forAmount(this.amount) : rest;
        if (!(this.amount instanceof StepAmount.SetsReps)
                && this.rest.mode != RestTimerPolicy.Mode.OFF)
            throw new IllegalArgumentException("Only set steps may configure a rest timer");
        if (!(this.amount instanceof StepAmount.SetsReps) && training != null)
            throw new IllegalArgumentException("Only set steps may have a training prescription");
        this.training = training;
    }

    public static StepPrescription forAmount(StepAmount amount) {
        return new StepPrescription(amount, RestTimerPolicy.forAmount(amount), null);
    }

    public static StepPrescription restore(StepAmount amount, RestTimerPolicy rest,
                                           ResistanceLoad load, int targetRir) {
        TrainingPrescription training = load == null || load.mode == ResistanceLoad.Mode.UNSPECIFIED
                ? null : new TrainingPrescription(load, targetRir);
        return new StepPrescription(amount, rest, training);
    }

    public ResistanceLoad plannedLoad() {
        return training == null ? ResistanceLoad.unspecified() : training.load;
    }

    public int targetRir() { return training == null ? 2 : training.targetRir; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof StepPrescription)) return false;
        StepPrescription value = (StepPrescription) other;
        return amount.equals(value.amount) && rest.equals(value.rest)
                && Objects.equals(training, value.training);
    }

    @Override public int hashCode() { return Objects.hash(amount, rest, training); }
}
