package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.RewardBreakdown;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusStepRowMode;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.StepExecutionUiAction;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayFocusProjector;
import de.thonktank.autosecretary.presentation.today.TodayReducer;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

public final class TodayFocusProjectorTest {
    @Test public void selectedRepetitionStepOwnsOrderModeAndFinalAction() {
        FocusStepUiModel normal = FocusTaskFixtures.simpleStep("a", "A", false);
        FocusStepUiModel repetitions = FocusTaskFixtures.step("b", "B")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.emptyList())).build();
        FocusStepUiModel delayed = FocusStepUiModel.executable("c", "C", "", "", false,
                StepExecutionUiAction.toggleWithDelay("c", 7_200_000L), null,
                RewardBreakdown.fromStage(10, 0), 0);
        TodayFeatureState idle = TodayFeatureState.idle(today(normal, repetitions, delayed));

        de.thonktank.autosecretary.presentation.today.FocusStepListUiModel projected =
                new TodayFocusProjector().project(idle.today, "b", idle.reorder);

        assertEquals(Arrays.asList("b", "a", "c"), Arrays.asList(
                projected.rows.get(0).id(), projected.rows.get(1).id(),
                projected.rows.get(2).id()));
        assertEquals(FocusStepRowMode.EXPANDED, projected.rows.get(0).mode);
        assertEquals(StepExecutionUiAction.Kind.SUBMIT_REPETITION,
                projected.rows.get(0).action.kind);
        assertEquals(FocusStepRowMode.COMPACT, projected.rows.get(1).mode);
        assertEquals(StepExecutionUiAction.Kind.ADVANCE_PLANNED_REPETITIONS,
                projected.rows.get(1).action.kind);
        assertEquals(StepExecutionUiAction.Kind.ADVANCE_PLANNED_REPETITIONS,
                projected.rows.get(2).action.kind);
    }

    @Test public void dragPreviewProjectsOrderWithoutReplacingCanonicalTodayModel() {
        TodayFeatureState idle = TodayFeatureState.idle(today(
                FocusTaskFixtures.simpleStep("a", "A", false),
                FocusTaskFixtures.simpleStep("b", "B", false),
                FocusTaskFixtures.simpleStep("c", "C", false)));
        TodayReducer reducer = new TodayReducer();
        TodayFeatureState dragging = reducer.begin(idle, "c",
                Arrays.asList("a", "b", "c")).state;
        TodayFeatureState preview = reducer.preview(dragging, "c",
                Arrays.asList("c", "a", "b")).state;

        assertSame(idle.today, preview.today);
        assertEquals(Arrays.asList("a", "b", "c"),
                TodayFeatureState.openStepIds(preview.today));
        assertEquals(Arrays.asList("c", "a", "b"), Arrays.asList(
                preview.focus.rows.get(0).id(), preview.focus.rows.get(1).id(),
                preview.focus.rows.get(2).id()));
    }

    private static TodayUiModel today(FocusStepUiModel... steps) {
        FocusTaskUiModel focus = FocusTaskFixtures.task("task", "Task")
                .occurrence("occurrence").steps(Arrays.asList(steps)).build();
        return new TodayUiModel(new XpProgress(0), focus, Collections.emptyList(),
                Collections.emptyList());
    }
}
