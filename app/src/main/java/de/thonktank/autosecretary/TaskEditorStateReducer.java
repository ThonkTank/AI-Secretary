package de.thonktank.autosecretary;

import java.util.ArrayList;
import java.util.List;

/** Pure step-editor transitions, independent from Android views and callbacks. */
public final class TaskEditorStateReducer {
    private TaskEditorStateReducer() { }

    public static EditorUiState addStep(EditorUiState state) {
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState added = EditorStepState.blank(state.nextDraftIdentity);
        steps.add(added);
        return replace(state, steps, added.id, state.nextDraftIdentity + 1);
    }

    public static EditorUiState updateStep(EditorUiState state, int index,
                                           EditorStepState step) {
        if (index < 0 || index >= state.stepStates.size()) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        steps.set(index, step);
        return replace(state, steps, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState removeStep(EditorUiState state, int index) {
        if (index < 0 || index >= state.stepStates.size()) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        steps.remove(index);
        return replace(state, steps, null, state.nextDraftIdentity);
    }

    public static EditorUiState moveStep(EditorUiState state, int from, int to) {
        if (from < 0 || from >= state.stepStates.size()
                || to < 0 || to >= state.stepStates.size() || from == to) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState moved = steps.remove(from);
        steps.add(to, moved);
        return replace(state, steps, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState expandStep(EditorUiState state, String id) {
        return replace(state, state.stepStates, id, state.nextDraftIdentity);
    }

    private static EditorUiState replace(EditorUiState state, List<EditorStepState> steps,
                                         String expandedStepId, int nextDraftIdentity) {
        return state.draft(state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, expandedStepId, nextDraftIdentity);
    }
}
