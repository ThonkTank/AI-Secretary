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
    public final StepAmount amount;
    public final String note;
    public final List<Integer> actualRepetitions;
    public final String sourceTemplateId;
    public final String comboOwnerId;

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done) {
        this(id, occurrenceId, position, text, done, StepAmount.none(), "",
                Collections.emptyList(), null, "step:" + id);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions) {
        this(id, occurrenceId, position, text, done, amount, note, actualRepetitions,
                null, "step:" + id);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions,
                          String comboOwnerId) {
        this(id, occurrenceId, position, text, done, amount, note, actualRepetitions,
                null, comboOwnerId);
    }

    public OccurrenceStep(String id, String occurrenceId, int position, String text, boolean done,
                          StepAmount amount, String note, List<Integer> actualRepetitions,
                          String sourceTemplateId, String comboOwnerId) {
        if (id == null || id.isEmpty() || occurrenceId == null || occurrenceId.isEmpty()
                || text == null || text.trim().isEmpty() || actualRepetitions == null
                || comboOwnerId == null)
            throw new IllegalArgumentException(
                    "Occurrence step identity, occurrence and text are required");
        TaskStepDefinition checked = new TaskStepDefinition(id, position, text, 0, amount, note);
        List<Integer> actual = new ArrayList<>();
        for (Integer value : actualRepetitions) {
            if (value == null || value < 0)
                throw new IllegalArgumentException("Confirmed repetitions must not be negative");
            actual.add(value);
        }
        boolean acceptsProgress = checked.amount instanceof StepAmount.SetsReps
                || checked.amount instanceof StepAmount.Repetitions;
        if (!acceptsProgress && !actual.isEmpty())
            throw new IllegalArgumentException("Step amount does not accept repetition progress");
        if (checked.amount instanceof StepAmount.SetsReps
                && actual.size() > ((StepAmount.SetsReps) checked.amount).sets)
            throw new IllegalArgumentException("Confirmed set count exceeds planned sets");
        if (checked.amount instanceof StepAmount.Repetitions && actual.size() > 1)
            throw new IllegalArgumentException("Single repetition progress has one value");
        this.id = id;
        this.occurrenceId = occurrenceId;
        this.position = checked.position;
        this.text = checked.text;
        this.amount = checked.amount;
        this.note = checked.note;
        this.actualRepetitions = Collections.unmodifiableList(actual);
        this.done = done;
        this.sourceTemplateId = sourceTemplateId == null || sourceTemplateId.isEmpty()
                ? null : sourceTemplateId;
        this.comboOwnerId = comboOwnerId.isEmpty() ? "step:" + id : comboOwnerId;
    }

    public OccurrenceStep toggle() {
        return new OccurrenceStep(id, occurrenceId, position, text, !done, amount, note,
                actualRepetitions, sourceTemplateId, comboOwnerId);
    }

    public OccurrenceStep complete() {
        return done ? this : new OccurrenceStep(id, occurrenceId, position, text, true,
                amount, note, actualRepetitions, sourceTemplateId, comboOwnerId);
    }

    public OccurrenceStep reopen() {
        return done ? new OccurrenceStep(id, occurrenceId, position, text, false, amount,
                note, actualRepetitions, sourceTemplateId, comboOwnerId) : this;
    }

    public OccurrenceStep confirmRepetitions(int repetitions) {
        if ((!(amount instanceof StepAmount.SetsReps)
                && !(amount instanceof StepAmount.Repetitions)) || done)
            throw new IllegalStateException("Step does not accept repetition progress");
        if (repetitions < 0 || repetitions > 999)
            throw new IllegalArgumentException("Repetitions must be between 0 and 999");
        List<Integer> values = new ArrayList<>(actualRepetitions);
        values.add(repetitions);
        int plannedSets = amount instanceof StepAmount.SetsReps
                ? ((StepAmount.SetsReps) amount).sets : 1;
        return new OccurrenceStep(id, occurrenceId, position, text,
                values.size() == plannedSets, amount, note, values,
                sourceTemplateId, comboOwnerId);
    }

    public OccurrenceStep confirmSet(int repetitions) {
        return confirmRepetitions(repetitions);
    }

    public OccurrenceStep withActualRepetitions(List<Integer> values) {
        return new OccurrenceStep(id, occurrenceId, position, text, done, amount, note,
                values, sourceTemplateId, comboOwnerId);
    }

    public int nextSetNumber() { return actualRepetitions.size() + 1; }
}
