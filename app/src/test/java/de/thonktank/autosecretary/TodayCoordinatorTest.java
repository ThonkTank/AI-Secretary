package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayReducer.ReorderWrite;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

public final class TodayCoordinatorTest {
    @Test public void immediateMoveProducesExactlyOnePersistenceCommand() {
        List<ReorderWrite> commands = new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, state -> { });

        coordinator.applyStepAction(TodayAction.moveStep("a", null));
        coordinator.applyStepAction(TodayAction.moveStep("a", null));

        assertEquals(1, commands.size());
        assertEquals(coordinator.state().reorder.commandId, commands.get(0).commandId());
        assertNull(commands.get(0).beforeStepId());
        assertEquals("a", commands.get(0).stepId());
    }

    @Test public void selectingAVisibleStepPublishesLocallyWithoutACommand() {
        List<ReorderWrite> commands = new ArrayList<>();
        List<TodayFeatureState> states = new ArrayList<>();
        TodayCoordinator coordinator = new TodayCoordinator(today(), commands::add, states::add);

        coordinator.applyStepAction(TodayAction.selectStep("c"));

        assertTrue(commands.isEmpty());
        assertEquals(1, states.size());
        assertEquals("c", states.get(0).selectedStepId);
        assertEquals("c", states.get(0).focus.rows.get(0).id());
    }

    private static TodayUiModel today() {
        FocusTaskUiModel focus = FocusTaskFixtures.task("task", "Task")
                .occurrence("occurrence").steps(Arrays.asList(
                        FocusTaskFixtures.simpleStep("a", "A", false),
                        FocusTaskFixtures.simpleStep("b", "B", false),
                        FocusTaskFixtures.simpleStep("c", "C", false))).build();
        return new TodayUiModel(new XpProgress(0), focus, Collections.emptyList(),
                Collections.emptyList());
    }
}
