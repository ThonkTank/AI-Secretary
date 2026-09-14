package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** The single immutable truth for one confirmed repetition slot. */
public final class SetResult {
    public final int repetitions;

    public SetResult(int repetitions) {
        this(repetitions, false);
    }

    private SetResult(int repetitions, boolean restored) {
        if (restored) {
            if (repetitions < 0)
                throw new IllegalArgumentException("Confirmed repetitions must not be negative");
        } else {
            RepetitionProgress.requireRecordableValue(repetitions);
        }
        this.repetitions = repetitions;
    }

    public static SetResult repetitions(int repetitions) {
        return new SetResult(repetitions);
    }

    /** Restores legacy values that may exceed today's input limit. */
    public static SetResult restore(int repetitions) {
        return new SetResult(repetitions, true);
    }

    @Override public boolean equals(Object other) {
        return other instanceof SetResult
                && repetitions == ((SetResult) other).repetitions;
    }

    @Override public int hashCode() { return Objects.hash(repetitions); }
}
