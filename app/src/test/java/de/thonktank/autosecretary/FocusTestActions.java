package de.thonktank.autosecretary;

import java.util.List;

/** Explicit no-op port used only by focused view tests. */
class FocusTestActions implements FocusTaskView.TaskActions, FocusStepRowView.Actions {
    @Override public void onComplete(TaskSnapshot task) { }
    @Override public void onCompleteRemaining(TaskSnapshot task) { }
    @Override public void onHarvest(TaskSnapshot task) { }
    @Override public void onDefer(TaskSnapshot task) { }
    @Override public void onToggleStep(String stepId) { }
    @Override public void onConfirmRepetitions(String stepId, int repetitions) { }
    @Override public void onEditRepetitions(String stepId, List<Integer> repetitions) { }
    @Override public void onRepetitionInputStateChanged(RepetitionInputState state) { }
}
