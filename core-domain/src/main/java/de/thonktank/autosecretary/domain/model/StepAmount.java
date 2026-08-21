package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Typed step amount. Draft values may be zero; executable domain objects require validity. */
public sealed interface StepAmount permits StepAmount.None, StepAmount.SetsReps,
        StepAmount.Repetitions, StepAmount.Duration {
    StepAmountKind kind();
    boolean isValid();

    static StepAmount none() { return None.INSTANCE; }
    static StepAmount setsReps(int sets, int repetitions) {
        return new SetsReps(sets, repetitions);
    }
    static StepAmount repetitions(int repetitions) { return new Repetitions(repetitions); }
    static StepAmount duration(int seconds) { return new Duration(seconds); }

    static StepAmount fromStorage(StepAmountKind kind, Integer sets,
                                  Integer repetitions, Integer durationSeconds) {
        StepAmountKind value = kind == null ? StepAmountKind.NONE : kind;
        if (value == StepAmountKind.SETS_REPS)
            return setsReps(orZero(sets), orZero(repetitions));
        if (value == StepAmountKind.REPS) return repetitions(orZero(repetitions));
        if (value == StepAmountKind.DURATION) return duration(orZero(durationSeconds));
        return none();
    }

    static StepAmount requireValid(StepAmount amount) {
        if (amount == null || !amount.isValid())
            throw new IllegalArgumentException("Step amount must contain positive targets");
        return amount;
    }

    private static int orZero(Integer value) { return value == null ? 0 : value; }

    final class None implements StepAmount {
        private static final None INSTANCE = new None();
        private None() { }
        @Override public StepAmountKind kind() { return StepAmountKind.NONE; }
        @Override public boolean isValid() { return true; }
        @Override public boolean equals(Object other) { return other instanceof None; }
        @Override public int hashCode() { return StepAmountKind.NONE.hashCode(); }
        @Override public String toString() { return "None"; }
    }

    final class SetsReps implements StepAmount {
        public final int sets;
        public final int repetitions;
        private SetsReps(int sets, int repetitions) {
            this.sets = sets;
            this.repetitions = repetitions;
        }
        @Override public StepAmountKind kind() { return StepAmountKind.SETS_REPS; }
        @Override public boolean isValid() { return sets > 0 && repetitions > 0; }
        @Override public boolean equals(Object other) {
            return other instanceof SetsReps && sets == ((SetsReps) other).sets
                    && repetitions == ((SetsReps) other).repetitions;
        }
        @Override public int hashCode() { return Objects.hash(sets, repetitions); }
        @Override public String toString() { return sets + "x" + repetitions; }
    }

    final class Repetitions implements StepAmount {
        public final int repetitions;
        private Repetitions(int repetitions) { this.repetitions = repetitions; }
        @Override public StepAmountKind kind() { return StepAmountKind.REPS; }
        @Override public boolean isValid() { return repetitions > 0; }
        @Override public boolean equals(Object other) {
            return other instanceof Repetitions
                    && repetitions == ((Repetitions) other).repetitions;
        }
        @Override public int hashCode() { return repetitions; }
        @Override public String toString() { return repetitions + " reps"; }
    }

    final class Duration implements StepAmount {
        public final int seconds;
        private Duration(int seconds) { this.seconds = seconds; }
        @Override public StepAmountKind kind() { return StepAmountKind.DURATION; }
        @Override public boolean isValid() { return seconds > 0; }
        @Override public boolean equals(Object other) {
            return other instanceof Duration && seconds == ((Duration) other).seconds;
        }
        @Override public int hashCode() { return seconds; }
        @Override public String toString() { return seconds + "s"; }
    }
}
