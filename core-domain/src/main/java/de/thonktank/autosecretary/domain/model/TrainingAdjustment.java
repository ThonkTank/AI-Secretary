package de.thonktank.autosecretary.domain.model;

import de.thonktank.autosecretary.domain.training.TrainingAdaptationEngine;

import java.time.LocalDate;

/** Auditable, reversible change to one reusable training step. */
public final class TrainingAdjustment {
    public enum State { APPLIED, UNDONE }

    public final String id;
    public final String templateId;
    public final String sourceOccurrenceStepId;
    public final TrainingAdaptationEngine.Reason reason;
    public final StepAmount.SetsReps before;
    public final ResistanceLoad beforeLoad;
    public final StepAmount.SetsReps after;
    public final ResistanceLoad afterLoad;
    public final LocalDate createdOn;
    public final State state;

    public TrainingAdjustment(String id, String templateId, String sourceOccurrenceStepId,
                              TrainingAdaptationEngine.Reason reason,
                              StepAmount.SetsReps before, ResistanceLoad beforeLoad,
                              StepAmount.SetsReps after, ResistanceLoad afterLoad,
                              LocalDate createdOn, State state) {
        if (id == null || id.isEmpty() || templateId == null || templateId.isEmpty()
                || sourceOccurrenceStepId == null || reason == null || before == null
                || beforeLoad == null || after == null || afterLoad == null
                || createdOn == null || state == null)
            throw new IllegalArgumentException("Complete training adjustment is required");
        this.id = id; this.templateId = templateId;
        this.sourceOccurrenceStepId = sourceOccurrenceStepId; this.reason = reason;
        this.before = before; this.beforeLoad = beforeLoad;
        this.after = after; this.afterLoad = afterLoad;
        this.createdOn = createdOn; this.state = state;
    }

    public TrainingAdjustment undone() {
        return new TrainingAdjustment(id, templateId, sourceOccurrenceStepId, reason,
                before, beforeLoad, after, afterLoad, createdOn, State.UNDONE);
    }
}
