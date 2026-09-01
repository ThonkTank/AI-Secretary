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
    public final StepPrescription nextPrescription;
    public final TrainingAssistantState nextState;
    public final LoadDirection loadDirection;

    public TrainingDecision(Action action, Reason reason,
                            StepPrescription nextPrescription,
                            TrainingAssistantState nextState, LoadDirection loadDirection) {
        if (action == null || reason == null || nextPrescription == null
                || nextPrescription.training == null || nextState == null
                || action == Action.REQUEST_NEXT_LOAD != (loadDirection != null))
            throw new IllegalArgumentException("Complete consistent training decision required");
        this.action = action;
        this.reason = reason;
        this.ruleVersion = RULE_VERSION;
        this.nextPrescription = nextPrescription;
        this.nextState = nextState;
        this.loadDirection = loadDirection;
    }

    public boolean changedFrom(StepPrescription before) {
        return !nextPrescription.equals(before);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TrainingDecision)) return false;
        TrainingDecision value = (TrainingDecision) other;
        return action == value.action && reason == value.reason
                && ruleVersion == value.ruleVersion
                && nextPrescription.equals(value.nextPrescription)
                && nextState.equals(value.nextState)
                && loadDirection == value.loadDirection;
    }

    @Override public int hashCode() {
        return Objects.hash(action, reason, ruleVersion, nextPrescription, nextState,
                loadDirection);
    }
}
