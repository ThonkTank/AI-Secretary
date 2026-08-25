package de.thonktank.autosecretary.presentation.today;


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
        START_DURATION_TIMER,
        PAUSE_TIMER,
        RESUME_TIMER,
        RESET_TIMER,
        OBSERVE_TIMER,
        PERSIST_REORDER
    }

    public final Kind kind;
    public final String id;
    public final String relatedId;
    public final String text;
    public final String commandId;
    public final int value;

    TodayCommand(Kind kind, String id, String relatedId, String text,
                 String commandId, int value) {
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
                                String beforeStepId) {
        return new TodayCommand(Kind.PERSIST_REORDER, stepId, beforeStepId, null,
                commandId, 0);
    }
}
