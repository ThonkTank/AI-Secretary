package de.thonktank.autosecretary.presentation.today;

/** Explicit command exposed by one focus-step reward control. */
public final class StepExecutionUiAction {
    public enum Kind {
        NONE,
        TOGGLE,
        TOGGLE_WITH_DELAY,
        SUBMIT_REPETITION,
        ADVANCE_PLANNED_REPETITIONS
    }

    public final Kind kind;
    public final String stepId;
    public final long proposedDelayMillis;

    private StepExecutionUiAction(Kind kind, String stepId, long proposedDelayMillis) {
        if (kind == null || (kind != Kind.NONE && (stepId == null || stepId.isEmpty())))
            throw new IllegalArgumentException("Executable step action requires an identity");
        if (proposedDelayMillis < 0L)
            throw new IllegalArgumentException("Proposed delay must not be negative");
        this.kind = kind;
        this.stepId = stepId;
        this.proposedDelayMillis = proposedDelayMillis;
    }

    public static StepExecutionUiAction none() {
        return new StepExecutionUiAction(Kind.NONE, null, 0L);
    }

    public static StepExecutionUiAction toggle(String stepId) {
        return new StepExecutionUiAction(Kind.TOGGLE, stepId, 0L);
    }

    public static StepExecutionUiAction toggleWithDelay(String stepId,
                                                        long proposedDelayMillis) {
        return new StepExecutionUiAction(Kind.TOGGLE_WITH_DELAY, stepId,
                proposedDelayMillis);
    }

    public static StepExecutionUiAction submitRepetition(String stepId) {
        return new StepExecutionUiAction(Kind.SUBMIT_REPETITION, stepId, 0L);
    }

    public static StepExecutionUiAction advancePlannedRepetitions(String stepId) {
        return new StepExecutionUiAction(Kind.ADVANCE_PLANNED_REPETITIONS, stepId, 0L);
    }
}
