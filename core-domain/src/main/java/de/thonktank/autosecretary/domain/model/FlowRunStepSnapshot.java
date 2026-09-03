package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable step payload captured when a run is created. */
public final class FlowRunStepSnapshot {
    public final String id;
    public final String runId;
    public final int position;
    public final String sourceTemplateId;
    public final String text;
    public final StepPrescription prescription;
    public final String note;
    public final FlowDelayPolicy delayAfter;
    public final Long chosenDelayMillis;

    private FlowRunStepSnapshot(String id, String runId, int position, String sourceTemplateId,
                                String text, StepPrescription prescription, String note,
                                FlowDelayPolicy delayAfter, Long chosenDelayMillis) {
        if (blank(id) || blank(runId) || position < 0 || blank(sourceTemplateId)
                || blank(text) || prescription == null)
            throw new IllegalArgumentException("Flow run step snapshot is incomplete");
        if (chosenDelayMillis != null) {
            if (delayAfter == null)
                throw new IllegalArgumentException("A chosen delay needs a delay rule");
            delayAfter.choose(chosenDelayMillis);
        }
        this.id = id;
        this.runId = runId;
        this.position = position;
        this.sourceTemplateId = sourceTemplateId;
        this.text = text.trim();
        this.prescription = prescription;
        this.note = note == null ? "" : note;
        this.delayAfter = delayAfter;
        this.chosenDelayMillis = chosenDelayMillis;
    }

    /** Complete persistence/test-fixture boundary. Product creation uses StepSnapshotFactory. */
    public static FlowRunStepSnapshot rehydrate(
            String id, String runId, int position, String sourceTemplateId,
            String text, StepPrescription prescription, String note,
            FlowDelayPolicy delayAfter, Long chosenDelayMillis) {
        return new FlowRunStepSnapshot(id, runId, position, sourceTemplateId, text,
                prescription, note, delayAfter, chosenDelayMillis);
    }

    public FlowRunStepSnapshot chooseDelay(long delayMillis) {
        if (delayAfter == null) throw new IllegalStateException("This step has no successor delay");
        delayAfter.choose(delayMillis);
        return new FlowRunStepSnapshot(id, runId, position, sourceTemplateId, text, prescription,
                note, delayAfter, delayMillis);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof FlowRunStepSnapshot)) return false;
        FlowRunStepSnapshot value = (FlowRunStepSnapshot) other;
        return id.equals(value.id) && runId.equals(value.runId) && position == value.position
                && sourceTemplateId.equals(value.sourceTemplateId) && text.equals(value.text)
                && prescription.equals(value.prescription) && note.equals(value.note)
                && Objects.equals(delayAfter, value.delayAfter)
                && Objects.equals(chosenDelayMillis, value.chosenDelayMillis);
    }

    @Override public int hashCode() {
        return Objects.hash(id, runId, position, sourceTemplateId, text, prescription,
                note, delayAfter, chosenDelayMillis);
    }
}
