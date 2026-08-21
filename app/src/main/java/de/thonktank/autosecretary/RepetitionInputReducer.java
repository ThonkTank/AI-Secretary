package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;

/** Pure reducer for repetition drafts and submissions. */
public final class RepetitionInputReducer {
    public static final class Submission {
        public final String stepId;
        public final int value;
        public final int editingIndex;

        private Submission(String stepId, int value, int editingIndex) {
            this.stepId = stepId;
            this.value = value;
            this.editingIndex = editingIndex;
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

    public Result reduce(RepetitionInputState current, TodayUiModel dashboard,
                         DashboardEvent event) {
        if (!(event instanceof DashboardEvent.Today))
            return new Result(current.reconcile(dashboard.focus), null);
        return reduce(current, dashboard, ((DashboardEvent.Today) event).action);
    }

    public Result reduce(RepetitionInputState current, TodayUiModel dashboard,
                         TodayAction action) {
        if (current == null || dashboard == null || action == null)
            throw new IllegalArgumentException("Reducer state, dashboard and event are required");
        String stepId = stepId(action);
        FocusStepUiModel active = activeRepetitionStep(dashboard);
        if (stepId == null || active == null || !active.id.equals(stepId))
            return new Result(current.reconcile(dashboard.focus), null);
        if (action.kind == TodayAction.Kind.ADJUST_REPETITION) {
            return new Result(current.adjust(active, action.value), null);
        }
        if (action.kind == TodayAction.Kind.EDIT_REPETITION) {
            return new Result(current.edit(active, action.value), null);
        }
        if (action.kind == TodayAction.Kind.SUBMIT_REPETITION) {
            return new Result(RepetitionInputState.idle(), new Submission(active.id,
                    current.valueFor(active), current.editingIndexFor(active)));
        }
        return new Result(current, null);
    }

    @Nullable private static String stepId(TodayAction action) {
        switch (action.kind) {
            case ADJUST_REPETITION:
            case EDIT_REPETITION:
            case SUBMIT_REPETITION:
                return action.id;
            default:
                return null;
        }
    }

    @Nullable private static FocusStepUiModel activeRepetitionStep(TodayUiModel dashboard) {
        FocusTaskUiModel focus = dashboard.focus;
        if (focus == null) return null;
        for (FocusStepUiModel step : focus.steps)
            if (!step.isDone()) return step.repetitionProgress == null ? null : step;
        return null;
    }
}
