package de.thonktank.autosecretary.presentation.today;


import de.thonktank.autosecretary.domain.model.RewardBreakdown;

/** Presentation-ready step used exclusively by the Today focus card. */
public final class FocusStepUiModel {
    public final String id;
    public final String title;
    public final String amountLabel;
    public final String note;
    public final boolean done;
    /** Action this step performs when the focus projection expands it. */
    public final StepExecutionUiAction activeAction;
    public final RepetitionProgressUiModel repetitionProgress;
    public final RewardBreakdown reward;
    public final int grainLevel;
    public final int earnedXp;
    public final int plannedXp;
    /** Positive only for a duration step; zero for every other amount type. */
    public final int durationSeconds;
    public final TrainingPromptUiModel trainingPrompt;

    private FocusStepUiModel(String id, String title, boolean done) {
        this(id, title, "", "", done,
                done ? StepExecutionUiAction.none() : StepExecutionUiAction.toggle(id), null,
                RewardBreakdown.fromStage(10, 0), 0, done ? 10 : 0, 10, 0, null);
    }

    private FocusStepUiModel(String id, String title,
                             String amountLabel, String note,
                             boolean done,
                             StepExecutionUiAction activeAction,
                             RepetitionProgressUiModel repetitionProgress,
                             RewardBreakdown reward, int grainLevel, int earnedXp, int plannedXp,
                             int durationSeconds, TrainingPromptUiModel trainingPrompt) {
        if (id == null || id.isEmpty() || title == null || title.trim().isEmpty()
                || amountLabel == null || note == null
                || activeAction == null || reward == null)
            throw new IllegalArgumentException("Focus step identity and title are required");
        if (repetitionProgress != null && amountLabel.isEmpty())
            throw new IllegalArgumentException("Repetition progress requires an amount label");
        if (!done && repetitionProgress != null
                && repetitionProgress.repetitions.size()
                == repetitionProgress.slotCount)
            throw new IllegalArgumentException("Complete repetition results cannot remain open");
        if (done && activeAction.kind != StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Completed steps cannot expose an action");
        if (activeAction.kind == StepExecutionUiAction.Kind.SUBMIT_REPETITION
                && repetitionProgress == null)
            throw new IllegalArgumentException("Repetition submission requires progress");
        if (!done && activeAction.kind == StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Every visible open step requires an action");
        if (!id.equals(activeAction.stepId)
                && activeAction.kind != StepExecutionUiAction.Kind.NONE)
            throw new IllegalArgumentException("Step action identity must match its row");
        this.id = id;
        this.title = title;
        this.amountLabel = amountLabel;
        this.note = note;
        this.done = done;
        this.activeAction = activeAction;
        this.repetitionProgress = repetitionProgress;
        this.reward = reward;
        this.grainLevel = Math.max(0, grainLevel);
        this.earnedXp = Math.max(0, earnedXp);
        this.plannedXp = Math.max(0, plannedXp);
        this.durationSeconds = Math.max(0, durationSeconds);
        this.trainingPrompt = trainingPrompt;
    }

    public static FocusStepUiModel of(String id, String title,
                                      boolean done) {
        return new FocusStepUiModel(id, title, done);
    }

    public static FocusStepUiModel executable(String id, String title,
                                      String amountLabel, String note,
                                      boolean done,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, action,
                repetitionProgress, reward, reward.comboStage, earnedXp, reward.resultXp, 0, null);
    }

    public static FocusStepUiModel executable(String id, String title,
                                      String amountLabel, String note,
                                      boolean done,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int earnedXp, int plannedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, action,
                repetitionProgress, reward, reward.comboStage, earnedXp, plannedXp, 0, null);
    }

    public static FocusStepUiModel executableWithGrainLevel(String id,
                                      String title, String amountLabel,
                                      String note, boolean done,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int grainLevel,
                                      int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, action,
                repetitionProgress, reward, grainLevel, earnedXp, reward.resultXp, 0, null);
    }

    public FocusStepUiModel withDurationSeconds(int seconds) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, activeAction,
                repetitionProgress, reward, grainLevel, earnedXp, plannedXp, seconds,
                trainingPrompt);
    }

    public FocusStepUiModel withTrainingPrompt(TrainingPromptUiModel value) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, activeAction,
                repetitionProgress, reward, grainLevel, earnedXp, plannedXp, durationSeconds,
                value);
    }

    public boolean isDone() { return done; }
}
