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
    public final List<SetResult> results;
    public final Completion completion;

    private RepetitionProgress(int plannedSlots, List<SetResult> results,
                               Completion completion) {
        if (plannedSlots <= 0)
            throw new IllegalArgumentException("Repetition progress needs planned slots");
        if (results == null || completion == null)
            throw new IllegalArgumentException("Repetition progress state is required");
        if (results.size() > plannedSlots)
            throw new IllegalArgumentException("Confirmed repetitions exceed planned slots");
        List<SetResult> checked = new ArrayList<>();
        for (SetResult value : results) {
            if (value == null)
                throw new IllegalArgumentException("Confirmed set results are required");
            checked.add(value);
        }
        if (completion == Completion.IN_PROGRESS && checked.size() == plannedSlots)
            throw new IllegalArgumentException("Complete repetition results cannot remain open");
        if (completion == Completion.RESULTS_COMPLETE && checked.size() != plannedSlots)
            throw new IllegalArgumentException("Result completion requires every planned slot");
        this.plannedSlots = plannedSlots;
        this.results = Collections.unmodifiableList(checked);
        this.completion = completion;
    }

    /** Restores persisted progress while canonicalizing formerly contradictory done flags. */
    public static RepetitionProgress restoreResults(int plannedSlots, List<SetResult> results,
                                                    boolean storedDone) {
        int size = results == null ? 0 : results.size();
        Completion completion = size == plannedSlots
                ? Completion.RESULTS_COMPLETE
                : storedDone ? Completion.COMPLETED_WITHOUT_RESULTS : Completion.IN_PROGRESS;
        return new RepetitionProgress(plannedSlots, results, completion);
    }

    public static RepetitionProgress forAmount(StepAmount amount, List<SetResult> results,
                                               boolean storedDone) {
        if (amount instanceof StepAmount.SetsReps)
            return restoreResults(((StepAmount.SetsReps) amount).sets, results, storedDone);
        if (amount instanceof StepAmount.Repetitions)
            return restoreResults(1, results, storedDone);
        if (results != null && !results.isEmpty())
            throw new IllegalArgumentException("Step amount does not accept repetition progress");
        return null;
    }

    public boolean completed() { return completion != Completion.IN_PROGRESS; }

    /** Immutable projection for repetition-only consumers. */
    public List<Integer> repetitions() {
        List<Integer> repetitions = new ArrayList<>();
        for (SetResult result : results) repetitions.add(result.repetitions);
        return Collections.unmodifiableList(repetitions);
    }

    /** Zero-based next slot, or -1 when every planned result is present. */
    public int nextOpenSlotIndex() {
        return results.size() == plannedSlots ? -1 : results.size();
    }

    public RepetitionProgress record(SetResult result) {
        if (completed())
            throw new IllegalStateException("Completed repetition progress cannot accept results");
        if (result == null) throw new IllegalArgumentException("Set result is required");
        List<SetResult> changed = new ArrayList<>(results);
        changed.add(result);
        return new RepetitionProgress(plannedSlots, changed,
                changed.size() == plannedSlots
                        ? Completion.RESULTS_COMPLETE : Completion.IN_PROGRESS);
    }

    public RepetitionProgress record(int repetitions) {
        return record(SetResult.repetitions(repetitions));
    }

    public RepetitionProgress correct(int index, SetResult result) {
        if (index < 0 || index >= results.size())
            throw new IllegalArgumentException("Confirmed repetition index is out of range");
        if (result == null) throw new IllegalArgumentException("Set result is required");
        List<SetResult> changed = new ArrayList<>(results);
        changed.set(index, result);
        return new RepetitionProgress(plannedSlots, changed, completion);
    }

    public RepetitionProgress correct(int index, int repetitions) {
        return correct(index, SetResult.repetitions(repetitions));
    }

    public RepetitionProgress completeWithoutResults() {
        return completed() ? this : new RepetitionProgress(plannedSlots, results,
                Completion.COMPLETED_WITHOUT_RESULTS);
    }

    /** Reopening a result-complete step removes its final result to keep the state consistent. */
    public RepetitionProgress reopen() {
        if (!completed()) return this;
        List<SetResult> reopened = new ArrayList<>(results);
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
                && results.equals(value.results)
                && completion == value.completion;
    }

    @Override public int hashCode() {
        return Objects.hash(plannedSlots, results, completion);
    }
}
