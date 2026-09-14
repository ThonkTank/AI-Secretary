package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.presentation.today.*;

public final class FlowChainProjectionTest {
    @Test public void earnedTauAndTaskComboAreIndependentOfStepProgress() {
        FlowChainUiModel chain = FlowChainFixtures.chain("a", "Buntwäsche", FlowChainUiModel.Mode.TAU, 4_000_000);
        assertEquals(FlowChainUiModel.Mode.TAU, chain.mode);
        assertEquals(10, chain.vessel.earnedXp);
        assertEquals(30, chain.vessel.plannedXp);
        assertEquals(20, chain.vessel.reward.resultXp);
        assertEquals(2, chain.vessel.reward.comboStage);
        assertEquals(1, chain.vessel.done);
    }

    @Test public void waitingUsesPersistedActionTimeAndExactWaitIdentity() {
        FlowChainUiModel chain = FlowChainFixtures.chain("a", "Buntwäsche", FlowChainUiModel.Mode.COUNTDOWN, 4_000_000);
        assertEquals(Long.valueOf(400_000), chain.waitStartedAt);
        assertEquals(Long.valueOf(4_000_000), chain.readyAt);
        assertEquals("flow-wait:a:a", chain.editableWaits.get(0).waitId);
        assertEquals(20, chain.vessel.earnedXp);
        assertEquals(FlowChainUiModel.Mode.COUNTDOWN, chain.mode);
    }

    @Test public void resourceWaitHasNoFictitiousDeadlineAndReadyIsFull() {
        FlowChainUiModel resource = FlowChainFixtures.chain("a", "Wäsche", FlowChainUiModel.Mode.RESOURCE, 4_000_000);
        assertEquals(FlowChainUiModel.Mode.RESOURCE, resource.mode); assertNull(resource.readyAt);
        FlowChainUiModel ready = FlowChainFixtures.chain("a", "Wäsche", FlowChainUiModel.Mode.READY, 4_000_000);
        assertTrue(ready.vessel.ready); assertEquals(ready.vessel.earnedXp, ready.vessel.plannedXp);
        assertEquals("a", ready.runId);
    }

    @Test public void executableParallelBranchWinsOverEarlierWait() {
        FlowRunSummary base = FlowChainFixtures.run("parallel", "Wäsche", FlowChainUiModel.Mode.COUNTDOWN, 4_000_000);
        FlowGraphDefinition.Node root = new FlowGraphDefinition.Node("s", "Wäsche", StepPrescription.forAmount(StepAmount.none()), "", FlowDelayPolicy.fixed(0));
        FlowGraphDefinition.Node wait = new FlowGraphDefinition.Node("a", "Waschen", root.prescription, "", FlowDelayPolicy.fixed(1000));
        FlowGraphDefinition.Node action = new FlowGraphDefinition.Node("b", "Sortieren", root.prescription, "", FlowDelayPolicy.fixed(0));
        FlowTileGraph graph = new FlowTileGraph(List.of("s", "a", "b"), List.of(new FlowTileGraph.Link("s", "a"), new FlowTileGraph.Link("s", "b")));
        List<FlowGraphRun.Step> steps = List.of(new FlowGraphRun.Step("s", root, FlowGraphRun.State.DONE, 0L, null, 0L, 10),
                new FlowGraphRun.Step("a", wait, FlowGraphRun.State.WAITING_TIME, 1000L, 2000L, 1000L, 10),
                new FlowGraphRun.Step("b", action, FlowGraphRun.State.AVAILABLE, null, null, null, 0));
        FlowGraphRun run = new FlowGraphRun("parallel", base.taskId, "s", graph, steps, List.of(), 0, false);
        FlowRunSummary summary = new FlowRunSummary(new FlowGraphRunRecord(run, "parallel", java.time.LocalDate.of(2026, 9, 14),
                TaskSlot.MORNING, 0, 0, 0, 0), "Wäsche", List.of());
        FlowChainUiModel model = new FlowChainUiModel(summary, Map.of(), new RewardTextFormatter(Locale.GERMANY));
        assertEquals(FlowChainUiModel.Mode.TAU, model.mode);
        assertEquals(Long.valueOf(2000), model.readyAt);
        assertEquals("flow-wait:a", model.editableWaits.get(0).waitId);
    }

    @Test public void largestUnitChangesWithoutResettingRadialProgress() {
        long end = 259_200_000;
        long[] remaining = {172800000, 86400000, 86399000, 3600000, 3599000, 60000, 59000, 1, 0};
        long[] expected = {2, 1, 23, 1, 59, 1, 59, 1, 0};
        FlowCountdown.Unit[] units = {FlowCountdown.Unit.DAYS, FlowCountdown.Unit.DAYS,
                FlowCountdown.Unit.HOURS, FlowCountdown.Unit.HOURS, FlowCountdown.Unit.MINUTES,
                FlowCountdown.Unit.MINUTES, FlowCountdown.Unit.SECONDS, FlowCountdown.Unit.SECONDS, FlowCountdown.Unit.SECONDS};
        for (int i = 0; i < remaining.length; i++) {
            FlowCountdown clock = new FlowCountdown(0, end, end - remaining[i]);
            assertEquals(expected[i], clock.value); assertEquals(units[i], clock.unit);
            assertEquals(remaining[i] / (float) end, clock.fraction, .00001f);
        }
        assertEquals(.5f, new FlowCountdown(1000, 5000, 3000).fraction, 0f);
        assertEquals(.75f, new FlowCountdown(1000, 9000, 3000).fraction, 0f);
        assertEquals(0f, new FlowCountdown(1000, 1000, 3000).fraction, 0f);
        assertEquals(1f, new FlowCountdown(1000, 5000, 0).fraction, 0f);
    }
}
