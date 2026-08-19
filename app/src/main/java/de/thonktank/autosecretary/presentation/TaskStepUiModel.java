package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Presentation-ready dashboard step. Views render these fields without domain formatting. */
public final class TaskStepUiModel {
    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String subtitle;
    @NonNull public final String amountLabel;
    @NonNull public final String note;
    public final boolean done;
    @Nullable public final RepetitionProgressUiModel repetitionProgress;
    public final int comboStage;
    public final int claimableXp;
    public final int earnedXp;

    public TaskStepUiModel(@NonNull String id, @NonNull String title, boolean done) {
        this(id, title, "", "", "", done, null, 0, 10, done ? 10 : 0);
    }

    public TaskStepUiModel(@NonNull String id, @NonNull String title,
                           @NonNull String subtitle, boolean done,
                           @Nullable RepetitionProgressUiModel repetitionProgress,
                           int comboStage, int claimableXp, int earnedXp) {
        this(id, title, subtitle, "", "", done, repetitionProgress,
                comboStage, claimableXp, earnedXp);
    }

    public TaskStepUiModel(@NonNull String id, @NonNull String title,
                           @NonNull String subtitle, @NonNull String amountLabel,
                           @NonNull String note, boolean done,
                           @Nullable RepetitionProgressUiModel repetitionProgress,
                           int comboStage, int claimableXp, int earnedXp) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.amountLabel = amountLabel;
        this.note = note;
        this.done = done;
        this.repetitionProgress = repetitionProgress;
        this.comboStage = Math.max(0, comboStage);
        this.claimableXp = Math.max(0, claimableXp);
        this.earnedXp = Math.max(0, earnedXp);
    }
}
