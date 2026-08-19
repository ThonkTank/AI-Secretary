package de.thonktank.autosecretary;

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
import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

public final class RepetitionInputReducerTest {
    private final RepetitionInputReducer reducer = new RepetitionInputReducer();

    @Test public void rapidAdjustmentsAccumulateBeforeOneSubmission() {
        FocusStepUiModel step = repetition("first", false, Collections.emptyList());
        TodayUiModel dashboard = today(task("task", step));
        RepetitionInputState state = RepetitionInputState.idle();

        state = reducer.reduce(state, dashboard,
                DashboardEvent.adjustRepetition(step.id, 1)).state;
        state = reducer.reduce(state, dashboard,
                DashboardEvent.adjustRepetition(step.id, 1)).state;
        RepetitionInputReducer.Result submitted = reducer.reduce(state, dashboard,
                DashboardEvent.submitRepetition(step.id));

        assertEquals(14, submitted.submission.value);
        assertFalse(submitted.submission.correction());
        assertNull(submitted.state.stepId);
    }

    @Test public void staleEventsCannotCrossAFocusOrAutomaticStepSwitch() {
        FocusStepUiModel oldStep = repetition("old-step", false, Collections.emptyList());
        RepetitionInputState draft = reducer.reduce(RepetitionInputState.idle(),
                today(task("old-task", oldStep)),
                DashboardEvent.adjustRepetition(oldStep.id, 3)).state;
        FocusStepUiModel nextStep = repetition("next-step", false, Collections.emptyList());
        TodayUiModel switched = today(task("new-task", nextStep));

        RepetitionInputReducer.Result staleAdjustment = reducer.reduce(draft, switched,
                DashboardEvent.adjustRepetition(oldStep.id, 1));
        RepetitionInputReducer.Result staleSubmission = reducer.reduce(
                staleAdjustment.state, switched,
                DashboardEvent.submitRepetition(oldStep.id));

        assertNull(staleAdjustment.state.stepId);
        assertNull(staleSubmission.submission);
        assertSame(staleAdjustment.state, staleSubmission.state);
        assertEquals(12, staleSubmission.state.valueFor(nextStep));
    }

    @Test public void correctionUsesTheSelectedSavedSlot() {
        FocusStepUiModel step = repetition("sets", false, Arrays.asList(10, 11));
        TodayUiModel dashboard = today(task("task", step));

        RepetitionInputState editing = reducer.reduce(RepetitionInputState.idle(), dashboard,
                DashboardEvent.editRepetition(step.id, 0)).state;
        editing = reducer.reduce(editing, dashboard,
                DashboardEvent.adjustRepetition(step.id, 1)).state;
        RepetitionInputReducer.Submission submitted = reducer.reduce(editing, dashboard,
                DashboardEvent.submitRepetition(step.id)).submission;

        assertEquals(0, submitted.editingIndex);
        assertEquals(11, submitted.value);
    }

    private static FocusStepUiModel repetition(String id, boolean done,
                                                java.util.List<Integer> actual) {
        return FocusStepUiModel.of(id, "Kniebeugen", "3 × 12", "", done,
                RepetitionProgressUiModel.sets(3, 12, actual), 0, 10, 0);
    }

    private static TaskSnapshot task(String id, FocusStepUiModel... steps) {
        return new TaskSnapshot(id, id + "-today", "Routine", TaskSlot.MORNING, "", "",
                Recurrence.DAILY, Arrays.asList(steps), steps.length,
                false, false, false, false, 0, 1L);
    }

    private static TodayUiModel today(TaskSnapshot focus) {
        return new TodayUiModel(0, new XpProgress(0),
                Collections.singletonList(focus), focus);
    }
}
