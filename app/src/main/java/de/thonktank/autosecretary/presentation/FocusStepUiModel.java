package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Presentation-ready step used exclusively by the Today focus card. */
public final class FocusStepUiModel {
    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String amountLabel;
    @NonNull public final String note;
    public final boolean done;
    @Nullable public final RepetitionProgressUiModel repetitionProgress;
    public final int comboStage;
    public final int claimableXp;
    public final int earnedXp;

    private FocusStepUiModel(@NonNull String id, @NonNull String title, boolean done) {
        this(id, title, "", "", done, null, 0, 10, done ? 10 : 0);
    }

    private FocusStepUiModel(@NonNull String id, @NonNull String title,
                             @NonNull String amountLabel, @NonNull String note, boolean done,
                             @Nullable RepetitionProgressUiModel repetitionProgress,
                             int comboStage, int claimableXp, int earnedXp) {
        if (id == null || id.isEmpty() || title == null || title.trim().isEmpty()
                || amountLabel == null || note == null)
            throw new IllegalArgumentException("Focus step identity and title are required");
        if (repetitionProgress != null && amountLabel.isEmpty())
            throw new IllegalArgumentException("Repetition progress requires an amount label");
        if (!done && repetitionProgress != null
                && repetitionProgress.actualRepetitions.size()
                == repetitionProgress.slotCount)
            throw new IllegalArgumentException("Complete repetition results cannot remain open");
        this.id = id;
        this.title = title;
        this.amountLabel = amountLabel;
        this.note = note;
        this.done = done;
        this.repetitionProgress = repetitionProgress;
        this.comboStage = Math.max(0, comboStage);
        this.claimableXp = Math.max(0, claimableXp);
        this.earnedXp = Math.max(0, earnedXp);
    }

    public static FocusStepUiModel of(@NonNull String id, @NonNull String title,
                                      boolean done) {
        return new FocusStepUiModel(id, title, done);
    }

    public static FocusStepUiModel of(@NonNull String id, @NonNull String title,
                                      @NonNull String amountLabel, @NonNull String note,
                                      boolean done,
                                      @Nullable RepetitionProgressUiModel repetitionProgress,
                                      int comboStage, int claimableXp, int earnedXp) {
        return new FocusStepUiModel(id, title, amountLabel, note, done, repetitionProgress,
                comboStage, claimableXp, earnedXp);
    }
}
