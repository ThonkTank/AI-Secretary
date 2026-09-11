package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.usecase.FlowGraphExecution;
import de.thonktank.autosecretary.domain.usecase.FlowGraphExecution.Capacity;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class FlowGraphExecutionTest {
    private final AtomicInteger ids = new AtomicInteger();
    private final FlowGraphExecution engine = new FlowGraphExecution(() -> "id:" + ids.incrementAndGet());
    private static final Capacity FREE = new Capacity(Collections.emptyMap(), Collections.emptyMap());

    @Test public void alternativeStartsExcludeOtherPrerequisitesAndHaveIndependentSnapshots() {
        FlowGraphDefinition definition = definition("b,w,h,a,p", "b:h,w:h,h:a,a:p", Collections.emptyMap());
        FlowGraphRun bunt = start(definition, "b", FREE);
        FlowGraphRun weiss = start(definition, "w", FREE);
        assertEquals(Arrays.asList("b", "h", "a", "p"), sourceIds(bunt));
        assertEquals(Arrays.asList("w", "h", "a", "p"), sourceIds(weiss));
        assertFalse(bunt.steps.keySet().stream().anyMatch(weiss.steps::containsKey));
        bunt = complete(bunt, "b", null, 0, FREE).run;
        assertEquals(Collections.singletonList("h"), available(bunt));
        assertEquals(Collections.singletonList("w"), available(weiss));
        assertEquals(Collections.singletonList(node(bunt, "b").id), bunt.graph.predecessors(node(bunt, "h").id));
    }

    @Test public void unequalBranchesJoinOnlyAfterBothActionsAndWaits() {
        FlowGraphRun run = start(definition("s,a,b,c,j", "s:a,s:b,a:c,c:j,b:j",
                Map.of("b", FlowDelayPolicy.fixed(20))), "s", FREE);
        run = complete(run, "s", null, 0, FREE).run;
        assertEquals(Arrays.asList("a", "b"), available(run));
        run = complete(run, "b", null, 100, FREE).run;
        run = complete(run, "a", null, 101, FREE).run;
        run = complete(run, "c", null, 102, FREE).run;
        assertTrue(available(run).isEmpty());
        assertEquals(FlowGraphRun.State.BLOCKED, node(run, "j").state);
        run = engine.settle(run, 119, FREE);
        assertTrue(available(run).isEmpty());
        run = engine.settle(run, 120, FREE);
        assertEquals(Collections.singletonList("j"), available(run));
    }

    @Test public void independentBranchesDoNotGainAnImplicitJoin() {
        FlowGraphRun run = start(definition("s,a,b,c", "s:a,s:b,a:c", Collections.emptyMap()), "s", FREE);
        run = complete(run, "s", null, 0, FREE).run;
        run = complete(run, "a", null, 0, FREE).run;
        assertEquals(Arrays.asList("b", "c"), available(run));
        run = complete(run, "c", null, 0, FREE).run;
        assertFalse(run.allDone());
        assertEquals(Collections.singletonList("b"), available(run));
    }

    @Test public void waitPromptOnAnyStepIncludingFinalStepDoesNotMutateOnCancel() {
        FlowGraphRun run = start(definition("s,a", "s:a", Map.of("a", FlowDelayPolicy.rememberLast(20))), "s", FREE);
        run = complete(run, "s", null, 0, FREE).run;
        FlowGraphExecution.Change prompt = complete(run, "a", null, 100, FREE);
        assertTrue(prompt.durationRequired);
        assertFalse(prompt.changed);
        assertSame(run, prompt.run);
        FlowGraphExecution.Change chosen = complete(run, "a", 35L, 100, FREE);
        assertEquals(Long.valueOf(135), node(chosen.run, "a").readyAtEpochMillis);
        assertFalse(chosen.run.collected);
        assertEquals(Long.valueOf(35), node(chosen.run, "a").chosenDelayMillis);
    }

    @Test public void extendingOneWaitPreservesTheOtherAndOnlyUnlocksItsOwnBranch() {
        FlowGraphRun run = start(definition("s,a,b,x,y", "s:a,s:b,a:x,b:y",
                Map.of("a", FlowDelayPolicy.fixed(10), "b", FlowDelayPolicy.fixed(20))), "s", FREE);
        run = complete(run, "s", null, 0, FREE).run;
        run = complete(run, "a", null, 100, FREE).run;
        run = complete(run, "b", null, 100, FREE).run;
        run = engine.adjustWait(run, node(run, "a").waitId(), 200L, 105L, FREE).run;
        assertEquals(Long.valueOf(120), node(run, "b").readyAtEpochMillis);
        assertEquals(Long.valueOf(10), node(run, "a").chosenDelayMillis);
        run = engine.settle(run, 120L, FREE);
        assertEquals(Collections.singletonList("y"), available(run));
        assertEquals(Long.valueOf(200), run.nextReadyAt());
        assertFalse(engine.adjustWait(run, run.id, 300L, 120L, FREE).changed);
    }

    @Test public void resourceWaitingDoesNotBlockAnIndependentBranch() {
        FlowGraphDefinition definition = withLeases(definition("s,a,b", "s:a,s:b", Collections.emptyMap()),
                new FlowGraphDefinition.Lease("machine", "m", "a", "a", 1, false));
        Capacity full = new Capacity(Map.of("m", 1), Map.of("m", 1L));
        FlowGraphRun run = start(definition, "s", full);
        run = complete(run, "s", null, 0, full).run;
        assertEquals(Collections.singletonList("b"), available(run));
        assertEquals(FlowGraphRun.State.WAITING_RESOURCE, node(run, "a").state);
        run = engine.settle(run, 0L, new Capacity(Map.of("m", 1), Collections.emptyMap()));
        assertEquals(Arrays.asList("a", "b"), available(run));
    }

    @Test public void sharedCapacitySerializesParallelActionsWithoutSerializingTheirGraph() {
        FlowGraphDefinition definition = withLeases(definition("s,a,b", "s:a,s:b", Collections.emptyMap()),
                new FlowGraphDefinition.Lease("a-m", "m", "a", "a", 1, false),
                new FlowGraphDefinition.Lease("b-m", "m", "b", "b", 1, false));
        Capacity one = new Capacity(Map.of("m", 1), Collections.emptyMap());
        FlowGraphRun run = complete(start(definition, "s", one), "s", null, 0, one).run;
        assertEquals(Collections.singletonList("a"), available(run));
        run = complete(run, "a", null, 0, one).run;
        assertEquals(Collections.singletonList("b"), available(run));
        assertEquals(Collections.singletonList(node(run, "s").id), run.graph.predecessors(node(run, "b").id));
    }

    @Test public void releaseAfterActionAndAfterWaitAreDistinctIncludingSameStep() {
        FlowGraphDefinition base = definition("s,n", "s:n", Map.of("s", FlowDelayPolicy.fixed(20)));
        Capacity capacity = new Capacity(Map.of("m", 1), Collections.emptyMap());
        FlowGraphRun actionRelease = start(withLeases(base,
                new FlowGraphDefinition.Lease("m", "m", "s", "s", 1, false)), "s", capacity);
        FlowGraphRun waitRelease = start(withLeases(base,
                new FlowGraphDefinition.Lease("m", "m", "s", "s", 1, true)), "s", capacity);
        actionRelease = complete(actionRelease, "s", null, 100, capacity).run;
        waitRelease = complete(waitRelease, "s", null, 100, capacity).run;
        assertEquals(FlowResourceState.RELEASED, actionRelease.leases.get(0).state);
        assertEquals(FlowResourceState.ACTIVE, waitRelease.leases.get(0).state);
        waitRelease = engine.settle(waitRelease, 120L, capacity);
        assertEquals(FlowResourceState.RELEASED, waitRelease.leases.get(0).state);
    }

    @Test public void loweringCapacityDoesNotRevokeExistingClaims() {
        FlowGraphDefinition definition = withLeases(definition("s,n", "s:n", Collections.emptyMap()),
                new FlowGraphDefinition.Lease("rack", "rack", "s", "n", 2, false));
        FlowGraphRun run = start(definition, "s", new Capacity(Map.of("rack", 3), Collections.emptyMap()));
        run = complete(run, "s", null, 0, new Capacity(Map.of("rack", 1), Collections.emptyMap())).run;
        assertEquals(FlowResourceState.ACTIVE, run.leases.get(0).state);
        assertEquals(Collections.singletonList("n"), available(run));
    }

    @Test public void finalActionCollectsOneRunTotalAndDoubleClickDoesNotPayAgain() {
        FlowGraphRun run = start(definition("s,a,b", "s:a,s:b", Collections.emptyMap()), "s", FREE);
        run = engine.complete(run, node(run, "s").id, null, 2, 0, FREE).run;
        run = engine.complete(run, node(run, "a").id, null, 3, 0, FREE).run;
        FlowGraphExecution.Change last = engine.complete(run, node(run, "b").id, null, 5, 0, FREE);
        assertEquals(10, last.paymentTau);
        assertEquals(last.run.collectionKey(), last.paymentKey);
        assertTrue(last.run.collected);
        assertEquals(0, engine.collect(last.run).paymentTau);
        assertFalse(engine.complete(last.run, node(last.run, "b").id, null, 5, 0, FREE).changed);
    }

    @Test public void trailingWaitCreatesOnlyOneCollectionActionAndNoAutomaticPayout() {
        FlowGraphRun run = start(definition("s", "", Map.of("s", FlowDelayPolicy.fixed(10))), "s", FREE);
        run = engine.complete(run, run.startStepId, null, 4, 0L, FREE).run;
        assertFalse(run.collectionAvailable());
        run = engine.settle(run, 10L, FREE);
        assertTrue(run.collectionAvailable());
        assertTrue(run.availableSteps().isEmpty());
        FlowGraphExecution.Change collected = engine.collect(run);
        assertEquals(4, collected.paymentTau);
        assertFalse(engine.collect(collected.run).changed);
    }

    @Test public void restoringAlreadyPaidRewardsOnlyCollectsTheUnpaidRemainder() {
        FlowGraphRun run = start(definition("s,a", "s:a", Collections.emptyMap()), "s", FREE);
        run = engine.complete(run, node(run, "s").id, null, 7, 0, FREE).run;
        run = new FlowGraphRun(run.id, run.taskId, run.startStepId, run.graph,
                new ArrayList<>(run.steps.values()), run.leases, 7, false);
        FlowGraphExecution.Change last = engine.complete(run, node(run, "a").id, null, 5, 0, FREE);
        assertEquals(5, last.paymentTau);
        assertEquals(12, last.run.alreadyPaidTau);
    }

    @Test public void snapshotPayloadAndSelectedWaitAreIndependentOfDefinitionEdits() {
        FlowGraphDefinition definition = definition("s", "", Map.of("s", FlowDelayPolicy.rememberLast(10)));
        FlowGraphRun run = start(definition, "s", FREE);
        FlowGraphDefinition edited = definition("s", "", Map.of("s", FlowDelayPolicy.fixed(100)));
        assertNotEquals(definition.nodes.get("s").waitAfter, edited.nodes.get("s").waitAfter);
        run = complete(run, "s", 25L, 0, FREE).run;
        assertEquals(FlowDelayPolicy.rememberLast(10), node(run, "s").source.waitAfter);
        assertEquals(Long.valueOf(25), node(run, "s").chosenDelayMillis);
    }

    @Test public void invalidOrForeignCommandsCannotMutateSnapshot() {
        FlowGraphRun run = start(definition("s,n", "s:n", Collections.emptyMap()), "s", FREE);
        assertFalse(complete(run, "n", null, 0, FREE).changed);
        assertFalse(engine.complete(run, "foreign", null, 0, 0, FREE).changed);
        assertThrows(IllegalArgumentException.class, () -> engine.snapshot(
                definition("s,n", "s:n", Collections.emptyMap()), "n"));
        assertThrows(IllegalArgumentException.class, () -> engine.complete(run, run.startStepId, null, -1, 0, FREE));
        assertEquals(Collections.singletonList("s"), available(run));
    }

    @Test public void clockTicksWithoutStateChangesDoNotCreateWritesOrNewOffers() {
        FlowGraphRun run = start(definition("s,n", "s:n", Map.of("s", FlowDelayPolicy.fixed(20))), "s", FREE);
        assertSame(run, engine.settle(run, 0, FREE));
        run = complete(run, "s", null, 0, FREE).run;
        assertSame(run, engine.settle(run, 10, FREE));
        assertFalse(engine.adjustWait(run, node(run, "s").waitId(), 20, 10, FREE).changed);
    }

    private FlowGraphRun start(FlowGraphDefinition definition, String start, Capacity capacity) {
        return engine.settle(engine.snapshot(definition, start), 0, capacity);
    }

    private FlowGraphExecution.Change complete(FlowGraphRun run, String source, Long chosen, long now, Capacity capacity) {
        return engine.complete(run, node(run, source).id, chosen, 1, now, capacity);
    }

    static FlowGraphRun.Step node(FlowGraphRun run, String source) {
        return run.steps.values().stream().filter(step -> step.source.id.equals(source)).findFirst().orElseThrow();
    }

    private static List<String> sourceIds(FlowGraphRun run) {
        List<String> result = new ArrayList<>();
        for (FlowGraphRun.Step step : run.steps.values()) result.add(step.source.id);
        return result;
    }

    private static List<String> available(FlowGraphRun run) {
        List<String> result = new ArrayList<>();
        for (FlowGraphRun.Step step : run.availableSteps()) result.add(step.source.id);
        return result;
    }

    static FlowGraphDefinition definition(String nodes, String edges, Map<String, FlowDelayPolicy> waits) {
        List<String> ids = Arrays.asList(nodes.split(","));
        List<FlowTileGraph.Link> links = new ArrayList<>();
        if (!edges.isEmpty()) for (String edge : edges.split(",")) {
            String[] pair = edge.split(":");
            links.add(new FlowTileGraph.Link(pair[0], pair[1]));
        }
        List<FlowGraphDefinition.Node> steps = new ArrayList<>();
        for (String id : ids) steps.add(new FlowGraphDefinition.Node(id, id,
                StepPrescription.forAmount(StepAmount.none()), "", waits.getOrDefault(id, FlowDelayPolicy.fixed(0))));
        return new FlowGraphDefinition(TaskId.of("task"), new FlowTileGraph(ids, links), steps, Collections.emptyList());
    }

    static FlowGraphDefinition withLeases(FlowGraphDefinition definition, FlowGraphDefinition.Lease... leases) {
        return new FlowGraphDefinition(definition.taskId, definition.graph,
                new ArrayList<>(definition.nodes.values()), Arrays.asList(leases));
    }
}
