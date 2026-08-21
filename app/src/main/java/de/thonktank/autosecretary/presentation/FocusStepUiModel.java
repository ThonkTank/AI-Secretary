package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.presentation.today.FocusStepStatus;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;

/** Presentation-ready step used exclusively by the Today focus card. */
public final class FocusStepUiModel {
    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String amountLabel;
    @NonNull public final String note;
    @NonNull public final FocusStepStatus status;
    @NonNull public final StepExecutionUiAction executionAction;
    @Nullable public final RepetitionProgressUiModel repetitionProgress;
    @NonNull public final RewardBreakdown reward;
    public final int grainLevel;
    public final int earnedXp;

    private FocusStepUiModel(@NonNull String id, @NonNull String title, boolean done) {
        this(id, title, "", "", done ? FocusStepStatus.COMPLETED : FocusStepStatus.ACTIVE,
                done ? StepExecutionUiAction.none() : StepExecutionUiAction.toggle(id), null,
                RewardBreakdown.fromStage(10, 0), 0, done ? 10 : 0);
    }

    private FocusStepUiModel(@NonNull String id, @NonNull String title,
                             @NonNull String amountLabel, @NonNull String note,
                             @NonNull FocusStepStatus status,
                             @NonNull StepExecutionUiAction executionAction,
                             @Nullable RepetitionProgressUiModel repetitionProgress,
                             @NonNull RewardBreakdown reward, int grainLevel, int earnedXp) {
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

    public static FocusStepUiModel of(@NonNull String id, @NonNull String title,
                                      boolean done) {
        return new FocusStepUiModel(id, title, done);
    }

    public static FocusStepUiModel executable(@NonNull String id, @NonNull String title,
                                      @NonNull String amountLabel, @NonNull String note,
                                      @NonNull FocusStepStatus status,
                                      @NonNull StepExecutionUiAction action,
                                      @Nullable RepetitionProgressUiModel repetitionProgress,
                                      @NonNull RewardBreakdown reward, int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, status, action,
                repetitionProgress, reward, reward.comboStage, earnedXp);
    }

    public static FocusStepUiModel executableWithGrainLevel(@NonNull String id,
                                      @NonNull String title, @NonNull String amountLabel,
                                      @NonNull String note, @NonNull FocusStepStatus status,
                                      @NonNull StepExecutionUiAction action,
                                      @Nullable RepetitionProgressUiModel repetitionProgress,
                                      @NonNull RewardBreakdown reward, int grainLevel,
                                      int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, status, action,
                repetitionProgress, reward, grainLevel, earnedXp);
    }

    public boolean isDone() { return status == FocusStepStatus.COMPLETED; }
}
