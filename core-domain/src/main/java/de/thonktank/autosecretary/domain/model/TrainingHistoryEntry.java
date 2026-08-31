package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** One stable, audit-ordered item in the explainable training history. */
public final class TrainingHistoryEntry {
    public enum Kind { ADJUSTMENT, LOAD_REQUEST }

    public final Kind kind;
    public final long auditOrder;
    public final LocalDate createdOn;
    public final TrainingDecision.Reason reason;
    public final TrainingAdjustment.State adjustmentState;
    public final TrainingLoadRequest.State requestState;
    public final TrainingLoadRequest.Resolution requestResolution;
    public final TrainingDecision.LoadDirection loadDirection;
    public final StepAmount.SetsReps before;
    public final ResistanceLoad beforeLoad;
    public final StepAmount.SetsReps after;
    public final ResistanceLoad afterLoad;

    private TrainingHistoryEntry(Kind kind, long auditOrder, LocalDate createdOn,
                                 TrainingDecision.Reason reason,
                                 TrainingAdjustment.State adjustmentState,
                                 TrainingLoadRequest.State requestState,
                                 TrainingLoadRequest.Resolution requestResolution,
                                 TrainingDecision.LoadDirection loadDirection,
                                 StepAmount.SetsReps before, ResistanceLoad beforeLoad,
                                 StepAmount.SetsReps after, ResistanceLoad afterLoad) {
        this.kind = kind;
        this.auditOrder = auditOrder;
        this.createdOn = createdOn;
        this.reason = reason;
        this.adjustmentState = adjustmentState;
        this.requestState = requestState;
        this.requestResolution = requestResolution;
        this.loadDirection = loadDirection;
        this.before = before;
        this.beforeLoad = beforeLoad;
        this.after = after;
        this.afterLoad = afterLoad;
    }

    public static TrainingHistoryEntry adjustment(TrainingAdjustment value) {
        return new TrainingHistoryEntry(Kind.ADJUSTMENT, value.auditOrder, value.createdOn,
                value.reason, value.state, null, null, null, value.before, value.beforeLoad,
                value.after, value.afterLoad);
    }

    public static TrainingHistoryEntry request(TrainingLoadRequest value) {
        return new TrainingHistoryEntry(Kind.LOAD_REQUEST, value.auditOrder, value.createdOn,
                null, null, value.state, value.resolution, value.direction, null,
                value.currentLoad, null, null);
    }
}
