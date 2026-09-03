package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.presentation.today.FocusStepListUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepRowUiModel;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;

/** Pure reducer for repetition drafts and submissions. */
public final class RepetitionInputReducer {
    public static final class Submission {
        public final String stepId;
        public final int value;
        public final int editingIndex;
        public final de.thonktank.autosecretary.domain.model.ResistanceLoad load;
        public final int rir;
        public final boolean safetyFlag;

        private Submission(String stepId, int value, int editingIndex,
                           de.thonktank.autosecretary.domain.model.ResistanceLoad load,
                           int rir, boolean safetyFlag) {
            this.stepId = stepId;
            this.value = value;
            this.editingIndex = editingIndex;
            this.load = load;
            this.rir = rir;
            this.safetyFlag = safetyFlag;
        }

        public boolean correction() { return editingIndex >= 0; }
    }

    public static final class Result {
        public final RepetitionInputState state;
        @Nullable public final Submission submission;

        private Result(RepetitionInputState state, @Nullable Submission submission) {
            this.state = state;
            this.submission = submission;
        }
    }

    public Result reduce(RepetitionInputState current, FocusStepListUiModel focus,
                         TodayAction action) {
        if (current == null || focus == null || action == null)
            throw new IllegalArgumentException("Reducer state, focus and event are required");
        String stepId = stepId(action);
        FocusStepUiModel active = activeRepetitionStep(focus);
        if (stepId == null || active == null || !active.id.equals(stepId))
            return new Result(current.reconcile(focus), null);
        if (action.kind == TodayAction.Kind.ADJUST_REPETITION) {
            return new Result(current.adjust(active, action.value), null);
        }
        if (action.kind == TodayAction.Kind.EDIT_REPETITION) {
            return new Result(current.edit(active, action.value), null);
        }
        if (action.kind == TodayAction.Kind.ADJUST_TRAINING_LOAD)
            return new Result(current.adjustLoad(active, action.value), null);
        if (action.kind == TodayAction.Kind.ADJUST_TRAINING_RIR)
            return new Result(current.adjustRir(active, action.value), null);
        if (action.kind == TodayAction.Kind.TOGGLE_TRAINING_SAFETY)
            return new Result(current.toggleSafety(active), null);
        if (action.kind == TodayAction.Kind.SUBMIT_REPETITION) {
            return new Result(RepetitionInputState.idle(), new Submission(active.id,
                    current.valueFor(active), current.editingIndexFor(active),
                    current.loadFor(active), current.rirFor(active), current.safetyFor(active)));
        }
        return new Result(current, null);
    }

    @Nullable private static String stepId(TodayAction action) {
        switch (action.kind) {
            case ADJUST_REPETITION:
            case ADJUST_TRAINING_LOAD:
            case ADJUST_TRAINING_RIR:
            case TOGGLE_TRAINING_SAFETY:
            case EDIT_REPETITION:
            case SUBMIT_REPETITION:
                return action.id;
            default:
                return null;
        }
    }

    @Nullable private static FocusStepUiModel activeRepetitionStep(FocusStepListUiModel focus) {
        FocusStepRowUiModel row = focus.expandedRow();
        return row == null || row.step.repetitionProgress == null ? null : row.step;
    }
}
