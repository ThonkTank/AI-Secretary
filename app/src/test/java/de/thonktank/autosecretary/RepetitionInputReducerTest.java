package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.FocusStepListUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;

public final class RepetitionInputReducerTest {
    private final RepetitionInputReducer reducer = new RepetitionInputReducer();

    @Test public void rapidAdjustmentsAccumulateBeforeOneSubmission() {
        FocusStepUiModel step = repetition("first", false, Collections.emptyList());
        TodayUiModel dashboard = today(task("task", step));
        RepetitionInputState state = RepetitionInputState.idle();

        state = reducer.reduce(state, focus(dashboard),
                TodayAction.adjustRepetition(step.id, 1)).state;
        state = reducer.reduce(state, focus(dashboard),
                TodayAction.adjustRepetition(step.id, 1)).state;
        RepetitionInputReducer.Result submitted = reducer.reduce(state, focus(dashboard),
                TodayAction.submitRepetition(step.id));

        assertEquals(14, submitted.submission.value);
        assertFalse(submitted.submission.correction());
        assertNull(submitted.state.stepId);
    }

    @Test public void staleEventsCannotCrossAFocusOrAutomaticStepSwitch() {
        FocusStepUiModel oldStep = repetition("old-step", false, Collections.emptyList());
        RepetitionInputState draft = reducer.reduce(RepetitionInputState.idle(),
                focus(today(task("old-task", oldStep))),
                TodayAction.adjustRepetition(oldStep.id, 3)).state;
        FocusStepUiModel nextStep = repetition("next-step", false, Collections.emptyList());
        TodayUiModel switched = today(task("new-task", nextStep));

        RepetitionInputReducer.Result staleAdjustment = reducer.reduce(draft, focus(switched),
                TodayAction.adjustRepetition(oldStep.id, 1));
        RepetitionInputReducer.Result staleSubmission = reducer.reduce(
                staleAdjustment.state, focus(switched),
                TodayAction.submitRepetition(oldStep.id));

        assertNull(staleAdjustment.state.stepId);
        assertNull(staleSubmission.submission);
        assertSame(staleAdjustment.state, staleSubmission.state);
        assertEquals(12, staleSubmission.state.valueFor(nextStep));
    }

    @Test public void correctionUsesTheSelectedSavedSlot() {
        FocusStepUiModel step = repetition("sets", false, Arrays.asList(10, 11));
        TodayUiModel dashboard = today(task("task", step));

        RepetitionInputState editing = reducer.reduce(RepetitionInputState.idle(), focus(dashboard),
                TodayAction.editRepetition(step.id, 0)).state;
        editing = reducer.reduce(editing, focus(dashboard),
                TodayAction.adjustRepetition(step.id, 1)).state;
        RepetitionInputReducer.Submission submitted = reducer.reduce(editing, focus(dashboard),
                TodayAction.submitRepetition(step.id)).submission;

        assertEquals(0, submitted.editingIndex);
        assertEquals(11, submitted.value);
    }

    private static FocusStepUiModel repetition(String id, boolean done,
                                                java.util.List<Integer> actual) {
        return FocusTaskFixtures.step(id, "Kniebeugen").amount("3 × 12").done(done)
                .repetition(RepetitionProgressUiModel.sets(3, 12, actual)).build();
    }

    private static FocusTaskUiModel task(String id, FocusStepUiModel... steps) {
        return FocusTaskFixtures.task(id, "Routine").occurrence(id + "-today")
                .slot(TaskSlot.MORNING).recurrence(Recurrence.DAILY)
                .steps(Arrays.asList(steps)).build();
    }

    private static TodayUiModel today(FocusTaskUiModel focus) {
        return new TodayUiModel(new XpProgress(0), focus,
                Collections.emptyList(), Collections.emptyList());
    }

    private static FocusStepListUiModel focus(TodayUiModel today) {
        return TodayFeatureState.idle(today).focus;
    }
}
