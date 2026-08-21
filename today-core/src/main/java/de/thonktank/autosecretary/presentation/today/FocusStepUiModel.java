package de.thonktank.autosecretary.presentation.today;


import de.thonktank.autosecretary.domain.model.RewardBreakdown;

/** Presentation-ready step used exclusively by the Today focus card. */
public final class FocusStepUiModel {
    public final String id;
    public final String title;
    public final String amountLabel;
    public final String note;
    public final FocusStepStatus status;
    public final StepExecutionUiAction executionAction;
    public final RepetitionProgressUiModel repetitionProgress;
    public final RewardBreakdown reward;
    public final int grainLevel;
    public final int earnedXp;

    private FocusStepUiModel(String id, String title, boolean done) {
        this(id, title, "", "", done ? FocusStepStatus.COMPLETED : FocusStepStatus.ACTIVE,
                done ? StepExecutionUiAction.none() : StepExecutionUiAction.toggle(id), null,
                RewardBreakdown.fromStage(10, 0), 0, done ? 10 : 0);
    }

    private FocusStepUiModel(String id, String title,
                             String amountLabel, String note,
                             FocusStepStatus status,
                             StepExecutionUiAction executionAction,
                             RepetitionProgressUiModel repetitionProgress,
                             RewardBreakdown reward, int grainLevel, int earnedXp) {
        if (id == null || id.isEmpty() || title == null || title.trim().isEmpty()
                || amountLabel == null || note == null || status == null
                || executionAction == null || reward == null)
            throw new IllegalArgumentException("Focus step identity and title are required");
        if (repetitionProgress != null && amountLabel.isEmpty())
            throw new IllegalArgumentException("Repetition progress requires an amount label");
        if (status != FocusStepStatus.COMPLETED && repetitionProgress != null
                && repetitionProgress.actualRepetitions.size()
                == repetitionProgress.slotCount)
            throw new IllegalArgumentException("Complete repetition results cannot remain open");
        if (status == FocusStepStatus.COMPLETED
                && executionAction.kind != StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Completed steps cannot expose an action");
        if (executionAction.kind == StepExecutionUiAction.Kind.SUBMIT_REPETITION
                && repetitionProgress == null)
            throw new IllegalArgumentException("Repetition submission requires progress");
        if (status != FocusStepStatus.COMPLETED
                && executionAction.kind == StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Every visible open step requires an action");
        if (!id.equals(executionAction.stepId)
                && executionAction.kind != StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Step action identity must match its row");
        this.id = id;
        this.title = title;
        this.amountLabel = amountLabel;
        this.note = note;
        this.status = status;
        this.executionAction = executionAction;
        this.repetitionProgress = repetitionProgress;
        this.reward = reward;
        this.grainLevel = Math.max(0, grainLevel);
        this.earnedXp = Math.max(0, earnedXp);
    }

    public static FocusStepUiModel of(String id, String title,
                                      boolean done) {
        return new FocusStepUiModel(id, title, done);
    }

    public static FocusStepUiModel executable(String id, String title,
                                      String amountLabel, String note,
                                      FocusStepStatus status,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, status, action,
                repetitionProgress, reward, reward.comboStage, earnedXp);
    }

    public static FocusStepUiModel executableWithGrainLevel(String id,
                                      String title, String amountLabel,
                                      String note, FocusStepStatus status,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int grainLevel,
                                      int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, status, action,
                repetitionProgress, reward, grainLevel, earnedXp);
    }

    public boolean isDone() { return status == FocusStepStatus.COMPLETED; }
}
