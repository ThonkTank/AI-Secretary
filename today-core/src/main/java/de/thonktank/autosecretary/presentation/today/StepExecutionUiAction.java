package de.thonktank.autosecretary.presentation.today;

/** Explicit command exposed by one focus-step reward control. */
public final class StepExecutionUiAction {
    public enum Kind {
        NONE,
        TOGGLE,
        TOGGLE_WITH_DELAY,
        TOGGLE_FLOW_RUN_STEP,
        TOGGLE_FLOW_RUN_STEP_WITH_DELAY,
        START_FLOW_CANDIDATE,
        START_FLOW_CANDIDATE_WITH_DELAY,
        COLLECT_FLOW,
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

    public static StepExecutionUiAction toggleFlowRunStep(String stepId) {
        return new StepExecutionUiAction(Kind.TOGGLE_FLOW_RUN_STEP, stepId, 0L);
    }

    public static StepExecutionUiAction toggleFlowRunStepWithDelay(String stepId,
                                                                   long delayMillis) {
        return new StepExecutionUiAction(Kind.TOGGLE_FLOW_RUN_STEP_WITH_DELAY, stepId,
                delayMillis);
    }

    public static StepExecutionUiAction startFlowCandidate(String candidateId) {
        return new StepExecutionUiAction(Kind.START_FLOW_CANDIDATE, candidateId, 0L);
    }

    public static StepExecutionUiAction startFlowCandidateWithDelay(String candidateId,
                                                                    long delayMillis) {
        return new StepExecutionUiAction(Kind.START_FLOW_CANDIDATE_WITH_DELAY, candidateId,
                delayMillis);
    }

    public static StepExecutionUiAction submitRepetition(String stepId) {
        return new StepExecutionUiAction(Kind.SUBMIT_REPETITION, stepId, 0L);
    }

    public static StepExecutionUiAction collectFlow(String runId) {
        return new StepExecutionUiAction(Kind.COLLECT_FLOW, runId, 0);
    }

    public static StepExecutionUiAction advancePlannedRepetitions(String stepId) {
        return new StepExecutionUiAction(Kind.ADVANCE_PLANNED_REPETITIONS, stepId, 0L);
    }
}
