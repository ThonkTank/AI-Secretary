package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain state for repetition-based steps.
 *
 * <p>Stored values are intentionally allowed above the current input limit so legacy data
 * remains readable. Every new or corrected value crosses {@link #requireRecordableValue(int)}.
 * Completion without all results is an explicit state used by commands such as
 * "complete remaining".</p>
 */
public final class RepetitionProgress {
    public static final int MIN_INPUT = 0;
    public static final int MAX_INPUT = 999;

    public enum Completion {
        IN_PROGRESS,
        RESULTS_COMPLETE,
        COMPLETED_WITHOUT_RESULTS
    }

    public final int plannedSlots;
    public final List<Integer> actualRepetitions;
    public final Completion completion;

    private RepetitionProgress(int plannedSlots, List<Integer> actual,
                               Completion completion) {
        if (plannedSlots <= 0)
            throw new IllegalArgumentException("Repetition progress needs planned slots");
        if (actual == null || completion == null)
            throw new IllegalArgumentException("Repetition progress state is required");
        if (actual.size() > plannedSlots)
            throw new IllegalArgumentException("Confirmed repetitions exceed planned slots");
        List<Integer> checked = new ArrayList<>();
        for (Integer value : actual) {
            if (value == null || value < 0)
                throw new IllegalArgumentException("Confirmed repetitions must not be negative");
            checked.add(value);
        }
        if (completion == Completion.IN_PROGRESS && checked.size() == plannedSlots)
            throw new IllegalArgumentException("Complete repetition results cannot remain open");
        if (completion == Completion.RESULTS_COMPLETE && checked.size() != plannedSlots)
            throw new IllegalArgumentException("Result completion requires every planned slot");
        this.plannedSlots = plannedSlots;
        this.actualRepetitions = Collections.unmodifiableList(checked);
        this.completion = completion;
    }

    /** Restores persisted progress while canonicalizing formerly contradictory done flags. */
    public static RepetitionProgress restore(int plannedSlots, List<Integer> actual,
                                             boolean storedDone) {
        int size = actual == null ? 0 : actual.size();
        Completion completion = size == plannedSlots
                ? Completion.RESULTS_COMPLETE
                : storedDone ? Completion.COMPLETED_WITHOUT_RESULTS : Completion.IN_PROGRESS;
        return new RepetitionProgress(plannedSlots, actual, completion);
    }

    public static RepetitionProgress forAmount(StepAmount amount, List<Integer> actual,
                                               boolean storedDone) {
        if (amount instanceof StepAmount.SetsReps)
            return restore(((StepAmount.SetsReps) amount).sets, actual, storedDone);
        if (amount instanceof StepAmount.Repetitions)
            return restore(1, actual, storedDone);
        if (actual != null && !actual.isEmpty())
            throw new IllegalArgumentException("Step amount does not accept repetition progress");
        return null;
    }

    public boolean completed() { return completion != Completion.IN_PROGRESS; }

    /** Zero-based next slot, or -1 when every planned result is present. */
    public int nextOpenSlotIndex() {
        return actualRepetitions.size() == plannedSlots ? -1 : actualRepetitions.size();
    }

    public RepetitionProgress record(int repetitions) {
        if (completed())
            throw new IllegalStateException("Completed repetition progress cannot accept results");
        requireRecordableValue(repetitions);
        List<Integer> changed = new ArrayList<>(actualRepetitions);
        changed.add(repetitions);
        return new RepetitionProgress(plannedSlots, changed,
                changed.size() == plannedSlots
                        ? Completion.RESULTS_COMPLETE : Completion.IN_PROGRESS);
    }

    public RepetitionProgress correct(int index, int repetitions) {
        if (index < 0 || index >= actualRepetitions.size())
            throw new IllegalArgumentException("Confirmed repetition index is out of range");
        requireRecordableValue(repetitions);
        List<Integer> changed = new ArrayList<>(actualRepetitions);
        changed.set(index, repetitions);
        return new RepetitionProgress(plannedSlots, changed, completion);
    }

    public RepetitionProgress completeWithoutResults() {
        return completed() ? this : new RepetitionProgress(plannedSlots, actualRepetitions,
                Completion.COMPLETED_WITHOUT_RESULTS);
    }

    /** Reopening a result-complete step removes its final result to keep the state consistent. */
    public RepetitionProgress reopen() {
        if (!completed()) return this;
        List<Integer> reopened = new ArrayList<>(actualRepetitions);
        if (completion == Completion.RESULTS_COMPLETE && !reopened.isEmpty())
            reopened.remove(reopened.size() - 1);
        return new RepetitionProgress(plannedSlots, reopened, Completion.IN_PROGRESS);
    }

    public static int requireRecordableValue(int value) {
        if (value < MIN_INPUT || value > MAX_INPUT)
            throw new IllegalArgumentException("Repetitions must be between 0 and 999");
        return value;
    }

    public static int clampInput(int value) {
        return Math.max(MIN_INPUT, Math.min(MAX_INPUT, value));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof RepetitionProgress)) return false;
        RepetitionProgress value = (RepetitionProgress) other;
        return plannedSlots == value.plannedSlots
                && actualRepetitions.equals(value.actualRepetitions)
                && completion == value.completion;
    }

    @Override public int hashCode() {
        return Objects.hash(plannedSlots, actualRepetitions, completion);
    }
}
