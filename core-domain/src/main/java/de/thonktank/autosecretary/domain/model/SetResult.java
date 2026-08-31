package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** The single immutable truth for one confirmed repetition slot. */
public final class SetResult {
    public final int repetitions;
    public final TrainingObservation training;

    public SetResult(int repetitions, TrainingObservation training) {
        this(repetitions, training, false);
    }

    private SetResult(int repetitions, TrainingObservation training, boolean restored) {
        if (restored) {
            if (repetitions < 0)
                throw new IllegalArgumentException("Confirmed repetitions must not be negative");
        } else {
            RepetitionProgress.requireRecordableValue(repetitions);
        }
        this.repetitions = repetitions;
        this.training = training;
    }

    public static SetResult repetitions(int repetitions) {
        return new SetResult(repetitions, null);
    }

    /** Restores legacy values that may exceed today's input limit. */
    public static SetResult restore(int repetitions, TrainingObservation training) {
        return new SetResult(repetitions, training, true);
    }

    @Override public boolean equals(Object other) {
        return other instanceof SetResult
                && repetitions == ((SetResult) other).repetitions
                && Objects.equals(training, ((SetResult) other).training);
    }

    @Override public int hashCode() { return Objects.hash(repetitions, training); }
}
