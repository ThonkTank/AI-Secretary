package de.thonktank.autosecretary.presentation.today;

/** Final, rendering-ready form of one row in the Today focus sheet. */
public final class FocusStepRowUiModel {
    public final FocusStepUiModel step;
    public final FocusStepRowMode mode;
    public final StepExecutionUiAction action;

    FocusStepRowUiModel(FocusStepUiModel step, FocusStepRowMode mode,
                        StepExecutionUiAction action) {
        if (step == null || mode == null || action == null)
            throw new IllegalArgumentException("Complete focus-step row is required");
        if (step.isDone() || !step.id.equals(action.stepId))
            throw new IllegalArgumentException("Projected action must match an open step");
        this.step = step;
        this.mode = mode;
        this.action = action;
    }

    public String id() { return step.id; }
    public boolean expanded() { return mode != FocusStepRowMode.COMPACT; }

    public static FocusStepRowUiModel expanded(FocusStepUiModel step) {
        return new FocusStepRowUiModel(step, FocusStepRowMode.EXPANDED, step.activeAction);
    }

    public static FocusStepRowUiModel compact(FocusStepUiModel step) {
        return new FocusStepRowUiModel(step, FocusStepRowMode.COMPACT,
                step.activeAction.isFlowExecution() ? step.activeAction
                        : StepExecutionUiAction.advancePlannedRepetitions(step.id));
    }


}
