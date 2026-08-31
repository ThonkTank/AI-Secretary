package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Durable question for a concrete available resistance load. */
public final class TrainingLoadRequest {
    public enum State { OPEN, RESOLVED, CANCELLED }
    public enum Resolution {
        PENDING,
        LOAD_APPLIED,
        NO_HIGHER_LOAD,
        MANUAL_CHANGE,
        SET_RESULT_CORRECTED,
        UNDONE
    }

    public final String id;
    public final String templateId;
    public final String sourceOccurrenceStepId;
    public final TrainingDecision.LoadDirection direction;
    public final ResistanceLoad currentLoad;
    public final LocalDate createdOn;
    public final long auditOrder;
    public final int ruleVersion;
    public final State state;
    public final Resolution resolution;
    public final LocalDate resolvedOn;

    public TrainingLoadRequest(String id, String templateId, String sourceOccurrenceStepId,
                               TrainingDecision.LoadDirection direction,
                               ResistanceLoad currentLoad, LocalDate createdOn,
                               long auditOrder, int ruleVersion, State state,
                               Resolution resolution, LocalDate resolvedOn) {
        boolean open = state == State.OPEN;
        if (id == null || id.isEmpty() || templateId == null || templateId.isEmpty()
                || sourceOccurrenceStepId == null || sourceOccurrenceStepId.isEmpty()
                || direction == null || currentLoad == null || !currentLoad.adjustable()
                || currentLoad.milliUnits == null || currentLoad.milliUnits <= 0
                || createdOn == null || auditOrder < 1 || ruleVersion < 1 || state == null
                || resolution == null || open != (resolution == Resolution.PENDING)
                || open == (resolvedOn != null))
            throw new IllegalArgumentException("Complete consistent load request is required");
        this.id = id;
        this.templateId = templateId;
        this.sourceOccurrenceStepId = sourceOccurrenceStepId;
        this.direction = direction;
        this.currentLoad = currentLoad;
        this.createdOn = createdOn;
        this.auditOrder = auditOrder;
        this.ruleVersion = ruleVersion;
        this.state = state;
        this.resolution = resolution;
        this.resolvedOn = resolvedOn;
    }

    public static TrainingLoadRequest open(String id, String templateId,
                                           String sourceOccurrenceStepId,
                                           TrainingDecision.LoadDirection direction,
                                           ResistanceLoad currentLoad, LocalDate createdOn,
                                           long auditOrder, int ruleVersion) {
        return new TrainingLoadRequest(id, templateId, sourceOccurrenceStepId, direction,
                currentLoad, createdOn, auditOrder, ruleVersion, State.OPEN,
                Resolution.PENDING, null);
    }

    public TrainingLoadRequest resolve(Resolution value, LocalDate date) {
        if (value != Resolution.LOAD_APPLIED && value != Resolution.NO_HIGHER_LOAD)
            throw new IllegalArgumentException("Resolution does not complete a load answer");
        return close(State.RESOLVED, value, date);
    }

    public TrainingLoadRequest cancel(Resolution value, LocalDate date) {
        if (value != Resolution.MANUAL_CHANGE
                && value != Resolution.SET_RESULT_CORRECTED
                && value != Resolution.UNDONE)
            throw new IllegalArgumentException("Resolution does not cancel a load answer");
        return close(State.CANCELLED, value, date);
    }

    private TrainingLoadRequest close(State nextState, Resolution nextResolution,
                                      LocalDate date) {
        if (state != State.OPEN || date == null)
            throw new IllegalStateException("Only an open load request can be closed");
        return new TrainingLoadRequest(id, templateId, sourceOccurrenceStepId, direction,
                currentLoad, createdOn, auditOrder, ruleVersion, nextState,
                nextResolution, date);
    }
}
