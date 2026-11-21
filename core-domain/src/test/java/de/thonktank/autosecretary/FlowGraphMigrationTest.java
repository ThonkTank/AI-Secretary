package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.usecase.FlowGraphExecution;
import de.thonktank.autosecretary.domain.usecase.MigrateLinearFlowExecution;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

/** Conversion contract, not yet a Room/schema-upgrade test. */
public final class FlowGraphMigrationTest {
    private final MigrateLinearFlowExecution migration = new MigrateLinearFlowExecution();
    private final FlowGraphExecution engine = new FlowGraphExecution(() -> { throw new AssertionError("Migration must retain IDs"); });
    private static final FlowGraphExecution.Capacity CAPACITY =
            new FlowGraphExecution.Capacity(Map.of("rack", 3), Collections.emptyMap());

    @Test public void timedRunRetainsChosenDelayAbsoluteExtensionIdsAndCapacity() {
        FlowRunSnapshot before = legacy(StepFlowRunState.WAITING_TIME, 1, FlowResourceState.ACTIVE, 0);
        FlowGraphRun run = migration.execute(before, Map.of("s0", 4L), 4L);
        assertEquals(before.run.id, run.id);
        assertEquals(Arrays.asList("s0", "s1", "s2"), run.graph.stepIds);
        assertEquals(FlowGraphRun.State.WAITING_TIME, run.steps.get("s0").state);
        assertEquals(FlowGraphRun.State.BLOCKED, run.steps.get("s1").state);
        assertEquals(Long.valueOf(999), run.steps.get("s0").readyAtEpochMillis);
        assertEquals(Long.valueOf(50), run.steps.get("s0").chosenDelayMillis);
        assertEquals("lease-run", run.leases.get(0).id);
        assertEquals("s0", run.leases.get(0).acquireStepId);
        assertEquals("s2", run.leases.get(0).releaseStepId);
        assertEquals(FlowResourceState.ACTIVE, run.leases.get(0).state);
        assertEquals(Long.valueOf(999), run.nextReadyAt());
        run = engine.settle(run, 999, CAPACITY);
        assertEquals("s1", run.availableSteps().get(0).id);
        assertEquals(4, run.alreadyPaidTau);
    }

    @Test public void offeredAndResourceWaitingPositionsBecomeIndependentStepStates() {
        for (StepFlowRunState oldState : Arrays.asList(StepFlowRunState.OFFERED, StepFlowRunState.WAITING_RESOURCE)) {
            FlowGraphRun run = migration.execute(legacy(oldState, 1, FlowResourceState.ACTIVE, 0), Map.of("s0", 4L), 0);
            assertEquals(FlowGraphRun.State.DONE, run.steps.get("s0").state);
            assertEquals(oldState == StepFlowRunState.OFFERED ? FlowGraphRun.State.AVAILABLE
                    : FlowGraphRun.State.WAITING_RESOURCE, run.steps.get("s1").state);
            assertEquals(FlowGraphRun.State.BLOCKED, run.steps.get("s2").state);
            assertEquals("template1", run.steps.get("s1").source.id);
        }
    }

    @Test public void postponedOfferKeepsItsAlreadyReservedCapacityWhileWaitingAgain() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.WAITING_TIME, 1,
                FlowResourceState.RESERVED, 1), Map.of("s0", 4L), 4L);
        assertEquals(FlowResourceState.RESERVED, run.leases.get(0).state);
        assertEquals(FlowGraphRun.State.BLOCKED, run.steps.get("s1").state);
        run = engine.settle(run, 999, CAPACITY);
        assertEquals(FlowGraphRun.State.AVAILABLE, run.steps.get("s1").state);
        assertEquals(FlowResourceState.RESERVED, run.leases.get(0).state);
    }

    @Test public void completedPaidRunNeverOffersAnotherCollection() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.COMPLETED, 2,
                FlowResourceState.RELEASED, 0), Map.of("s0", 4L, "s1", 3L, "s2", 2L), 9L);
        assertTrue(run.collected);
        assertFalse(run.collectionAvailable());
        assertTrue(run.availableSteps().isEmpty());
        assertFalse(engine.collect(run).changed);
        assertEquals(0, engine.collect(run).paymentTau);
    }

    @Test public void completedPartlyPaidRunCollectsOnlyOutstandingLedgerAmount() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.COMPLETED, 2,
                FlowResourceState.RELEASED, 0), Map.of("s0", 4L, "s1", 3L, "s2", 2L), 7L);
        assertTrue(run.collectionAvailable());
        assertEquals(2L, engine.collect(run).paymentTau);
    }

    @Test public void historicallyCancelledRunNeverResumesOrPaysAndPreservesEarnedHistory() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.CANCELLED, 1,
                FlowResourceState.RELEASED, 0), Map.of("s0", 4L), 4L);
        assertTrue(run.cancelled);
        assertTrue(run.availableSteps().isEmpty());
        assertNull(run.nextReadyAt());
        assertSame(run, engine.settle(run, 2000, CAPACITY));
        assertFalse(engine.complete(run, "s1", null, 3, 2000, CAPACITY).changed);
        assertFalse(engine.collect(run).changed);
        assertEquals(4, run.earnedTau());
    }

    @Test public void invalidRewardOrCursorDataFailsClosedInsteadOfPayingOrDroppingData() {
        FlowRunSnapshot run = legacy(StepFlowRunState.OFFERED, 1, FlowResourceState.ACTIVE, 0);
        assertThrows(IllegalArgumentException.class, () -> migration.execute(run, Map.of("foreign", 4L), 0));
        assertThrows(IllegalArgumentException.class, () -> migration.execute(run, Map.of("s1", -4L), 0));
        assertThrows(IllegalArgumentException.class, () -> migration.execute(run, Map.of("s0", 4L), -1));
        assertThrows(IllegalArgumentException.class, () -> migration.execute(
                legacy(StepFlowRunState.WAITING_TIME, 0, FlowResourceState.PLANNED, 0), Collections.emptyMap(), 0));
    }

    @Test public void partialSetRewardsSurvivePostponementAndAreNotAddedTwiceOnCompletion() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.WAITING_TIME, 1,
                FlowResourceState.RESERVED, 1), Map.of("s0", 4L, "s1", 2L), 6L);
        assertEquals(2, run.steps.get("s1").earnedTau);
        run = engine.settle(run, 999, CAPACITY);
        assertEquals(2, run.steps.get("s1").earnedTau);
        run = engine.complete(run, "s1", 0L, 3L, 999L, CAPACITY).run;
        FlowGraphExecution.Change last = engine.complete(run, "s2", null, 2L, 999L, CAPACITY);
        assertEquals(9L, last.run.earnedTau());
        assertEquals(3L, last.paymentTau);
    }

    @Test public void correctingAnAlreadyHarvestedPartialAmountDoesNotErasePastPayments() {
        FlowGraphRun run = migration.execute(legacy(StepFlowRunState.OFFERED, 1,
                FlowResourceState.RESERVED, 1), Map.of("s0", 4L, "s1", 2L), 10L);
        run = engine.complete(run, "s1", 0L, 1L, 999L, CAPACITY).run;
        FlowGraphExecution.Change last = engine.complete(run, "s2", null, 2L, 999L, CAPACITY);
        assertEquals(0L, last.paymentTau);
        assertEquals(10L, last.run.alreadyPaidTau);
        assertTrue(last.run.collected);
    }

    private static FlowRunSnapshot legacy(StepFlowRunState state, int cursor,
                                          FlowResourceState resourceState, int acquire) {
        StepFlowRun run = new StepFlowRun("run", TaskId.of("task"), "template0", "source",
                LocalDate.of(2026, 9, 10), TaskSlot.MORNING, state, cursor,
                state == StepFlowRunState.WAITING_TIME ? 999L : null, "existing-occurrence", 1234L, 3, 100L, 200L);
        List<FlowRunStepSnapshot> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) steps.add(FlowRunStepSnapshot.rehydrate("s" + i, run.id, i,
                "template" + i, "Step " + i, StepPrescription.forAmount(StepAmount.none()), "preserved note",
                i == 2 ? null : FlowDelayPolicy.rememberLast(25), i == 0 ? 50L : null));
        FlowRunResourceSnapshot resource = new FlowRunResourceSnapshot("lease-run", run.id, "lease-definition",
                "rack", "Wäscheständer", 3, 1, acquire, 2, resourceState,
                resourceState == FlowResourceState.PLANNED ? null : 100L,
                resourceState == FlowResourceState.ACTIVE ? 100L : null,
                resourceState == FlowResourceState.RELEASED ? 200L : null);
        return new FlowRunSnapshot(run, steps, Collections.singletonList(resource));
    }
}
