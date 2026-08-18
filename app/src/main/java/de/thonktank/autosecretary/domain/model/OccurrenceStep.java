package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OccurrenceStep {
    public final String id;
    public final String occurrenceId;
    public final int position;
    public final String text;
    public final boolean done;
    public final StepAmountKind amountKind;
    public final Integer plannedSets;
    public final Integer plannedReps;
    public final Integer plannedDurationSeconds;
    public final String note;
    public final List<Integer> actualRepetitions;
    public final String comboOwnerId;
    public final int earnedXp;
    public final int comboPointDelta;

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done) {
        this(id, occurrenceId, position, text, done, StepAmountKind.NONE,
                null, null, null, "", Collections.emptyList(), "step:" + id, 0, 0);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmountKind amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, String note,
                          List<Integer> actualRepetitions) {
        this(id, occurrenceId, position, text, done, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, note, actualRepetitions, "step:" + id, 0, 0);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmountKind amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, String note,
                          List<Integer> actualRepetitions, String comboOwnerId,
                          int earnedXp, int comboPointDelta) {
        if (id == null || id.isEmpty() || occurrenceId == null || occurrenceId.isEmpty()
                || text == null || text.trim().isEmpty() || amountKind == null
                || actualRepetitions == null || comboOwnerId == null)
            throw new IllegalArgumentException("Occurrence step identity, occurrence and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, 0, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note);
        List<Integer> actual = new ArrayList<>();
        for (Integer value : actualRepetitions) {
            if (value == null || value <= 0)
                throw new IllegalArgumentException("Confirmed repetitions must be positive");
            actual.add(value);
        }
        if (amountKind != StepAmountKind.SETS_REPS && !actual.isEmpty())
            throw new IllegalArgumentException("Only set-based steps have repetition progress");
        if (checked.plannedSets != null && actual.size() > checked.plannedSets)
            throw new IllegalArgumentException("Confirmed set count exceeds planned sets");
        this.id = id;
        this.occurrenceId = occurrenceId;
        this.position = checked.position;
        this.text = checked.text;
        this.amountKind = checked.amountKind;
        this.plannedSets = checked.plannedSets;
        this.plannedReps = checked.plannedReps;
        this.plannedDurationSeconds = checked.plannedDurationSeconds;
        this.note = checked.note;
        this.actualRepetitions = Collections.unmodifiableList(actual);
        this.done = done;
        this.comboOwnerId = comboOwnerId.isEmpty() ? "step:" + id : comboOwnerId;
        this.earnedXp = Math.max(0, earnedXp);
        this.comboPointDelta = comboPointDelta;
    }

    public OccurrenceStep toggle() {
        return new OccurrenceStep(id, occurrenceId, position, text, !done, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note, actualRepetitions,
                comboOwnerId, done ? 0 : earnedXp, done ? 0 : comboPointDelta);
    }

    public OccurrenceStep complete() {
        return done ? this : new OccurrenceStep(id, occurrenceId, position, text, true,
                amountKind, plannedSets, plannedReps, plannedDurationSeconds, note,
                actualRepetitions, comboOwnerId, earnedXp, comboPointDelta);
    }

    public OccurrenceStep confirmSet(int repetitions) {
        if (amountKind != StepAmountKind.SETS_REPS || done)
            throw new IllegalStateException("Step does not accept another set");
        if (repetitions <= 0) throw new IllegalArgumentException("Repetitions must be positive");
        List<Integer> values = new ArrayList<>(actualRepetitions);
        values.add(repetitions);
        return new OccurrenceStep(id, occurrenceId, position, text,
                values.size() == plannedSets, amountKind, plannedSets, plannedReps,
                plannedDurationSeconds, note, values, comboOwnerId, earnedXp, comboPointDelta);
    }

    public OccurrenceStep award(int xp, int pointDelta) {
        return new OccurrenceStep(id, occurrenceId, position, text, true, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note, actualRepetitions,
                comboOwnerId, xp, pointDelta);
    }

    public OccurrenceStep resetReward() {
        return new OccurrenceStep(id, occurrenceId, position, text, false, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note, actualRepetitions,
                comboOwnerId, 0, 0);
    }

    public OccurrenceStep withActualRepetitions(List<Integer> values) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, amountKind,
                plannedSets, plannedReps, plannedDurationSeconds, note, values,
                comboOwnerId, earnedXp, comboPointDelta);
    }

    public int nextSetNumber() { return actualRepetitions.size() + 1; }
}
