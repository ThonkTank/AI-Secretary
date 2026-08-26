package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Immutable step payload captured when a run is created. */
public final class FlowRunStepSnapshot {
    public final String id;
    public final String runId;
    public final int position;
    public final String sourceTemplateId;
    public final String text;
    public final StepAmount amount;
    public final RestTimerPolicy restTimerPolicy;
    public final ResistanceLoad plannedLoad;
    public final int targetRir;
    public final String note;
    public final FlowDelayPolicy delayAfter;
    public final Long chosenDelayMillis;

    public FlowRunStepSnapshot(String id, String runId, int position, String sourceTemplateId,
                               String text, StepAmount amount, String note,
                               FlowDelayPolicy delayAfter, Long chosenDelayMillis) {
        this(id, runId, position, sourceTemplateId, text, amount,
                RestTimerPolicy.forAmount(amount), ResistanceLoad.unspecified(), 2,
                note, delayAfter, chosenDelayMillis);
    }

    public FlowRunStepSnapshot(String id, String runId, int position, String sourceTemplateId,
                               String text, StepAmount amount,
                               RestTimerPolicy restTimerPolicy, String note,
                               FlowDelayPolicy delayAfter, Long chosenDelayMillis) {
        this(id, runId, position, sourceTemplateId, text, amount, restTimerPolicy,
                ResistanceLoad.unspecified(), 2, note, delayAfter, chosenDelayMillis);
    }

    public FlowRunStepSnapshot(String id, String runId, int position, String sourceTemplateId,
                               String text, StepAmount amount,
                               RestTimerPolicy restTimerPolicy, ResistanceLoad plannedLoad,
                               int targetRir, String note,
                               FlowDelayPolicy delayAfter, Long chosenDelayMillis) {
        if (blank(id) || blank(runId) || position < 0 || blank(sourceTemplateId)
                || blank(text) || amount == null)
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
        this.amount = amount;
        this.restTimerPolicy = restTimerPolicy == null
                ? RestTimerPolicy.forAmount(amount) : restTimerPolicy;
        if (!(amount instanceof StepAmount.SetsReps)
                && this.restTimerPolicy.mode != RestTimerPolicy.Mode.OFF)
            throw new IllegalArgumentException("Only set steps may configure a rest timer");
        this.plannedLoad = plannedLoad == null ? ResistanceLoad.unspecified() : plannedLoad;
        if (targetRir < 0 || targetRir > 5)
            throw new IllegalArgumentException("Target RIR must be between zero and five");
        this.targetRir = targetRir;
        this.note = note == null ? "" : note;
        this.delayAfter = delayAfter;
        this.chosenDelayMillis = chosenDelayMillis;
    }

    public FlowRunStepSnapshot chooseDelay(long delayMillis) {
        if (delayAfter == null) throw new IllegalStateException("This step has no successor delay");
        delayAfter.choose(delayMillis);
        return new FlowRunStepSnapshot(id, runId, position, sourceTemplateId, text, amount,
                restTimerPolicy, plannedLoad, targetRir, note, delayAfter, delayMillis);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof FlowRunStepSnapshot)) return false;
        FlowRunStepSnapshot value = (FlowRunStepSnapshot) other;
        return id.equals(value.id) && runId.equals(value.runId) && position == value.position
                && sourceTemplateId.equals(value.sourceTemplateId) && text.equals(value.text)
                && amount.equals(value.amount) && restTimerPolicy.equals(value.restTimerPolicy)
                && plannedLoad.equals(value.plannedLoad) && targetRir == value.targetRir
                && note.equals(value.note)
                && Objects.equals(delayAfter, value.delayAfter)
                && Objects.equals(chosenDelayMillis, value.chosenDelayMillis);
    }

    @Override public int hashCode() {
        return Objects.hash(id, runId, position, sourceTemplateId, text, amount,
                restTimerPolicy, plannedLoad, targetRir, note, delayAfter, chosenDelayMillis);
    }
}
