package de.thonktank.autosecretary.presentation.today;


import de.thonktank.autosecretary.domain.model.RewardBreakdown;

/** Presentation-ready step used exclusively by the Today focus card. */
public final class FocusStepUiModel {
    public final String id;
    public final String title;
    public final String amountLabel;
    public final String note;
    public final String noteTargetId;
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

    private FocusStepUiModel(String id, String title, boolean done) {
        this(id, title, "", "", done,
                done ? StepExecutionUiAction.none() : StepExecutionUiAction.toggle(id), null,
                RewardBreakdown.fromStage(10, 0), 0, done ? 10 : 0, 10, 0);
    }

    private FocusStepUiModel(String id, String title,
                             String amountLabel, String note,
                             boolean done,
                             StepExecutionUiAction activeAction,
                             RepetitionProgressUiModel repetitionProgress,
                             RewardBreakdown reward, int grainLevel, int earnedXp, int plannedXp,
                             int durationSeconds) {
        this(id, title, amountLabel, note, done, activeAction, repetitionProgress, reward,
                grainLevel, earnedXp, plannedXp, durationSeconds, id);
    }

    private FocusStepUiModel(String id, String title, String amountLabel, String note,
                             boolean done, StepExecutionUiAction activeAction,
                             RepetitionProgressUiModel repetitionProgress, RewardBreakdown reward,
                             int grainLevel, int earnedXp, int plannedXp, int durationSeconds,
                             String noteTargetId) {
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
        this.noteTargetId = noteTargetId;
        this.done = done;
        this.activeAction = activeAction;
        this.repetitionProgress = repetitionProgress;
        this.reward = reward;
        this.grainLevel = Math.max(0, grainLevel);
        this.earnedXp = Math.max(0, earnedXp);
        this.plannedXp = Math.max(0, plannedXp);
        this.durationSeconds = Math.max(0, durationSeconds);
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
                repetitionProgress, reward, reward.comboStage, earnedXp, reward.resultXp, 0);
    }

    public static FocusStepUiModel executable(String id, String title,
                                      String amountLabel, String note,
                                      boolean done,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int earnedXp, int plannedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, action,
                repetitionProgress, reward, reward.comboStage, earnedXp, plannedXp, 0);
    }

    public static FocusStepUiModel executableWithGrainLevel(String id,
                                      String title, String amountLabel,
                                      String note, boolean done,
                                      StepExecutionUiAction action,
                                      RepetitionProgressUiModel repetitionProgress,
                                      RewardBreakdown reward, int grainLevel,
                                      int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, action,
                repetitionProgress, reward, grainLevel, earnedXp, reward.resultXp, 0);
    }

    public FocusStepUiModel withDurationSeconds(int seconds) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, activeAction,
                repetitionProgress, reward, grainLevel, earnedXp, plannedXp, seconds, noteTargetId);
    }



    public FocusStepUiModel withNoteTarget(String targetId) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, activeAction,
                repetitionProgress, reward, grainLevel, earnedXp, plannedXp, durationSeconds, targetId);
    }

    public boolean isDone() { return done; }
}
