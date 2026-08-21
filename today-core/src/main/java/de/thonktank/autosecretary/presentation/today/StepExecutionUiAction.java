package de.thonktank.autosecretary.presentation.today;

/** Explicit command exposed by one focus-step reward control. */
public final class StepExecutionUiAction {
    public enum Kind { NONE, TOGGLE, SUBMIT_REPETITION, ADVANCE_PLANNED_REPETITIONS }

    public final Kind kind;
    public final String stepId;

    private StepExecutionUiAction(Kind kind, String stepId) {
        if (kind == null || (kind != Kind.NONE && (stepId == null || stepId.isEmpty())))
            throw new IllegalArgumentException("Executable step action requires an identity");
        this.kind = kind;
        this.stepId = stepId;
    }

    public static StepExecutionUiAction none() {
        return new StepExecutionUiAction(Kind.NONE, null);
    }

    public static StepExecutionUiAction toggle(String stepId) {
        return new StepExecutionUiAction(Kind.TOGGLE, stepId);
    }

    public static StepExecutionUiAction submitRepetition(String stepId) {
        return new StepExecutionUiAction(Kind.SUBMIT_REPETITION, stepId);
    }

    public static StepExecutionUiAction advancePlannedRepetitions(String stepId) {
        return new StepExecutionUiAction(Kind.ADVANCE_PLANNED_REPETITIONS, stepId);
    }
}
