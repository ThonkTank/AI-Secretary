package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

/** One side-effect request emitted by {@link TodayCoordinator}. */
public final class TodayCommand {
    public enum Kind {
        COMPLETE_OCCURRENCE,
        REQUEST_CLOSE,
        COMPLETE_REMAINING,
        HARVEST,
        DEFER,
        TOGGLE_STEP,
        ADVANCE_STEP,
        UNDO_OCCURRENCE,
        ADJUST_REPETITION,
        EDIT_REPETITION,
        SUBMIT_REPETITION,
        PERSIST_REORDER
    }

    public final Kind kind;
    public final String id;
    @Nullable public final String relatedId;
    @Nullable public final String text;
    @Nullable public final String commandId;
    public final int value;

    TodayCommand(Kind kind, String id, @Nullable String relatedId, @Nullable String text,
                 @Nullable String commandId, int value) {
        this.kind = kind;
        this.id = id;
        this.relatedId = relatedId;
        this.text = text;
        this.commandId = commandId;
        this.value = value;
    }

    static TodayCommand action(Kind kind, TodayAction action) {
        return new TodayCommand(kind, action.id, action.relatedId, action.text, null,
                action.value);
    }

    static TodayCommand reorder(String commandId, String stepId,
                                @Nullable String beforeStepId) {
        return new TodayCommand(Kind.PERSIST_REORDER, stepId, beforeStepId, null,
                commandId, 0);
    }
}
