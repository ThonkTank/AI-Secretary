package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Presentation-ready dashboard step. Views render these fields without domain formatting. */
public final class TaskStepUiModel {
    @NonNull public final String id;
    @NonNull public final String title;
    @NonNull public final String subtitle;
    public final boolean done;
    @Nullable public final SetProgressUiModel setProgress;
    public final int comboStage;
    public final int claimableXp;
    public final int earnedXp;

    public TaskStepUiModel(@NonNull String id, @NonNull String title, boolean done) {
        this(id, title, "", done, null, 0, 10, done ? 10 : 0);
    }

    public TaskStepUiModel(@NonNull String id, @NonNull String title,
                           @NonNull String subtitle, boolean done,
                           @Nullable SetProgressUiModel setProgress,
                           int comboStage, int claimableXp, int earnedXp) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.done = done;
        this.setProgress = setProgress;
        this.comboStage = Math.max(0, comboStage);
        this.claimableXp = Math.max(0, claimableXp);
        this.earnedXp = Math.max(0, earnedXp);
    }
}
