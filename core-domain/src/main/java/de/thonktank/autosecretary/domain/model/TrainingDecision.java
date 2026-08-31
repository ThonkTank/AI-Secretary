package de.thonktank.autosecretary.domain.model;

import java.util.Objects;

/** Versioned, explainable output of the deterministic training rules. */
public final class TrainingDecision {
    public static final int RULE_VERSION = 1;

    public enum Action { HOLD, APPLY, REQUEST_NEXT_LOAD, PAUSE }
    public enum LoadDirection { PROGRESS, REGRESS }
    public enum Reason {
        NONE,
        CALIBRATING,
        REPETITIONS_INCREASED,
        NEXT_LOAD_REQUIRED,
        LOWER_LOAD_REQUIRED,
        LOAD_APPLIED,
        SET_ADDED,
        REPETITIONS_REDUCED,
        SET_REMOVED,
        VOLUME_LIMIT,
        BOUNDARY_REACHED,
        SAFETY_PAUSE,
        MANUAL_CHANGE,
        SET_RESULT_CORRECTED,
        UNDONE
    }

    public final Action action;
    public final Reason reason;
    public final int ruleVersion;
    public final StepAmount.SetsReps prescription;
    public final ResistanceLoad load;
    public final TrainingAssistantState state;
    public final LoadDirection loadDirection;

    public TrainingDecision(Action action, Reason reason,
                            StepAmount.SetsReps prescription, ResistanceLoad load,
                            TrainingAssistantState state, LoadDirection loadDirection) {
        if (action == null || reason == null || prescription == null || load == null
                || state == null || action == Action.REQUEST_NEXT_LOAD != (loadDirection != null))
            throw new IllegalArgumentException("Complete consistent training decision required");
        this.action = action;
        this.reason = reason;
        this.ruleVersion = RULE_VERSION;
        this.prescription = prescription;
        this.load = load;
        this.state = state;
        this.loadDirection = loadDirection;
    }

    public boolean changedFrom(StepAmount.SetsReps before, ResistanceLoad beforeLoad) {
        return !prescription.equals(before) || !load.equals(beforeLoad);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingDecision)) return false;
        TrainingDecision value = (TrainingDecision) other;
        return action == value.action && reason == value.reason
                && ruleVersion == value.ruleVersion && prescription.equals(value.prescription)
                && load.equals(value.load) && state.equals(value.state)
                && loadDirection == value.loadDirection;
    }

    @Override public int hashCode() {
        return Objects.hash(action, reason, ruleVersion, prescription, load, state, loadDirection);
    }
}
