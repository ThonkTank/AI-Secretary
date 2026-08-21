package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayReducer;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

public final class TodayReducerTest {
    private final TodayReducer reducer = new TodayReducer();
    private final List<String> canonical = Arrays.asList("a", "b", "c");

    @Test public void beginPreviewAndCancelRestoreCanonicalWithoutCommand() {
        TodayFeatureState state = TodayFeatureState.idle(today("a", "b", "c"));

        TodayReducer.Result begun = reducer.begin(state, "a", canonical);
        assertEquals(TodayFeatureState.Reorder.Phase.DRAGGING, begun.state.reorder.phase);
        assertEquals(canonical, begun.state.reorder.canonicalOrder);
        assertEquals(canonical, begun.state.reorder.previewOrder);
        assertNull(begun.command);

        TodayReducer.Result preview = reducer.preview(begun.state, "a",
                Arrays.asList("b", "a", "c"));
        assertEquals(Arrays.asList("b", "a", "c"), preview.state.reorder.previewOrder);
        assertEquals(Arrays.asList("b", "a", "c"), openIds(preview.state.today));
        assertNull(preview.command);

        TodayReducer.Result cancelled = reducer.cancel(preview.state, "a");
        assertEquals(TodayFeatureState.Reorder.Phase.IDLE, cancelled.state.reorder.phase);
        assertEquals(canonical, openIds(cancelled.state.today));
        assertNull(cancelled.command);
    }

    @Test public void dropEmitsExactlyOneCommandAndDuplicateDropIsIgnored() {
        TodayFeatureState dragging = reducer.begin(TodayFeatureState.idle(
                today("a", "b", "c")), "a", canonical).state;
        dragging = reducer.preview(dragging, "a", Arrays.asList("b", "a", "c")).state;

        TodayReducer.Result dropped = reducer.drop(dragging, "a", "c", "command-1");
        assertEquals(TodayFeatureState.Reorder.Phase.PERSISTING,
                dropped.state.reorder.phase);
        assertNotNull(dropped.command);
        assertEquals("command-1", dropped.command.commandId);

        TodayReducer.Result duplicate = reducer.drop(dropped.state, "a", "c", "command-1");
        assertSame(dropped.state, duplicate.state);
        assertNull(duplicate.command);
    }

    @Test public void successAdoptsConfirmedOrderAndFailureRestoresCanonical() {
        TodayFeatureState persisting = persisting();
        TodayStepMoveResult confirmed = new TodayStepMoveResult(
                TodayStepMoveResult.Status.MOVED, Collections.emptyList(),
                Arrays.asList("b", "a", "c"), Collections.emptyList());

        TodayReducer.Result succeeded = reducer.succeeded(persisting, "command-1", confirmed);
        assertEquals(Arrays.asList("b", "a", "c"), openIds(succeeded.state.today));
        assertEquals(TodayFeatureState.Reorder.Phase.IDLE, succeeded.state.reorder.phase);
        assertNull(succeeded.state.feedback);

        TodayReducer.Result failed = reducer.failed(persisting, "command-1");
        assertEquals(canonical, openIds(failed.state.today));
        assertEquals(TodayFeatureState.Feedback.REORDER_FAILED, failed.state.feedback);
    }

    @Test public void rebindDiscardsPreviewAndReportsConcurrentRefresh() {
        TodayFeatureState dragging = reducer.preview(reducer.begin(
                TodayFeatureState.idle(today("a", "b", "c")), "a", canonical).state,
                "a", Arrays.asList("b", "a", "c")).state;

        TodayReducer.Result rebound = reducer.rebind(dragging, today("c", "b", "a"));

        assertEquals(TodayFeatureState.Reorder.Phase.IDLE, rebound.state.reorder.phase);
        assertEquals(Arrays.asList("c", "b", "a"), rebound.state.reorder.canonicalOrder);
        assertEquals(TodayFeatureState.Feedback.REORDER_INTERRUPTED, rebound.state.feedback);
        assertNull(rebound.command);
    }

    private TodayFeatureState persisting() {
        TodayFeatureState state = reducer.begin(TodayFeatureState.idle(
                today("a", "b", "c")), "a", canonical).state;
        state = reducer.preview(state, "a", Arrays.asList("b", "a", "c")).state;
        return reducer.drop(state, "a", "c", "command-1").state;
    }

    private static TodayUiModel today(String... ids) {
        java.util.ArrayList<de.thonktank.autosecretary.presentation.today.FocusStepUiModel> steps =
                new java.util.ArrayList<>();
        for (String id : ids) steps.add(FocusTaskFixtures.simpleStep(id, id, false));
        FocusTaskUiModel focus = FocusTaskFixtures.task("task", "Task")
                .occurrence("occurrence").steps(steps).build();
        return new TodayUiModel(new XpProgress(0), focus, Collections.emptyList(),
                Collections.emptyList());
    }

    private static List<String> openIds(TodayUiModel model) {
        return TodayFeatureState.openStepIds(model);
    }
}
