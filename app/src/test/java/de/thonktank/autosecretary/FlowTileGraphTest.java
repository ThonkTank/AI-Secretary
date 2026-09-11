package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import de.thonktank.autosecretary.domain.model.FlowDefinitionException;
import de.thonktank.autosecretary.domain.model.FlowTileGraph;
import de.thonktank.autosecretary.domain.model.FlowTileGraph.Link;
import de.thonktank.autosecretary.domain.model.FlowTileGraph.Placement;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class FlowTileGraphTest {
    @Test public void alternativeLaundryStartsDoNotBecomeJoinRequirements() {
        FlowTileGraph graph = new FlowTileGraph(Arrays.asList("b", "w", "h", "a", "p"),
                Arrays.asList(link("b", "h"), link("w", "h"), link("h", "a"), link("a", "p")));
        FlowTileGraph run = graph.reachableFrom("b");
        assertEquals(Arrays.asList("b", "h", "a", "p"), run.topologicalOrder());
        assertEquals(Collections.singletonList("b"), run.predecessors("h"));
        assertEquals(Arrays.asList("b", "w"), graph.predecessors("h"));
    }

    @Test public void unequalParallelBranchesKeepBothJoinPredecessors() {
        FlowTileGraph graph = dinner();
        assertEquals(Arrays.asList("b", "d"), graph.predecessors("s"));
        assertEquals(Arrays.asList("v", "k", "d", "b", "s"), graph.topologicalOrder());
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("k", "b")), graph.branchFrom("k"));
        assertFalse(graph.branchFrom("k").contains("s"));
    }

    @Test public void placementIsAPureProposalAndDoesNotImplicitlyJoinParallelStep() {
        FlowTileGraph original = new FlowTileGraph(Arrays.asList("a", "b", "c", "new"),
                Arrays.asList(link("a", "b"), link("b", "c")));
        FlowTileGraph proposal = original.place("new", "b", Placement.BESIDE, false);
        assertEquals(Collections.singletonList("a"), proposal.predecessors("new"));
        assertTrue(proposal.successors("new").isEmpty());
        assertEquals(Collections.singletonList("b"), proposal.predecessors("c"));
        assertTrue(original.predecessors("new").isEmpty());
        FlowTileGraph joined = proposal.join(Collections.singletonList("new"), "c");
        assertEquals(Arrays.asList("b", "new"), joined.predecessors("c"));
        assertEquals(original.stepIds.size(), joined.stepIds.size());
    }

    @Test public void insertionBeforeAndAfterPreservesAllIdentities() {
        FlowTileGraph g = new FlowTileGraph(Arrays.asList("a", "b", "x"),
                Collections.singletonList(link("a", "b")));
        FlowTileGraph before = g.place("x", "b", Placement.BEFORE, false);
        FlowTileGraph after = g.place("x", "a", Placement.AFTER, false);
        assertEquals(Arrays.asList("a", "x", "b"), before.topologicalOrder());
        assertEquals(before.topologicalOrder(), after.topologicalOrder());
        assertEquals(Arrays.asList(link("a", "x"), link("x", "b")), before.links);
    }

    @Test public void movingBranchDoesNotMoveItsJoin() {
        FlowTileGraph result = dinner().place("k", "d", Placement.AFTER, true);
        assertEquals(Collections.singletonList("d"), result.predecessors("k"));
        assertEquals(Collections.singletonList("k"), result.predecessors("b"));
        assertEquals(Collections.singletonList("b"), result.predecessors("s"));
    }

    @Test public void cyclesDuplicateEdgesAndForeignTargetsAreRejected() {
        assertThrows(FlowDefinitionException.class, () -> dinner().join(Collections.singletonList("s"), "v"));
        assertThrows(FlowDefinitionException.class, () -> dinner().place("k", "b", Placement.BESIDE, true));
        assertThrows(FlowDefinitionException.class, () -> new FlowTileGraph(Arrays.asList("a", "a"), Collections.emptyList()));
        assertThrows(FlowDefinitionException.class, () -> new FlowTileGraph(Arrays.asList("a", "b"), Arrays.asList(link("a", "b"), link("a", "b"))));
        assertThrows(FlowDefinitionException.class, () -> dinner().join(Collections.singletonList("missing"), "s"));
    }

    private static Link link(String a, String b) { return new Link(a, b); }
    private static FlowTileGraph dinner() {
        return new FlowTileGraph(Arrays.asList("v", "k", "d", "b", "s"), Arrays.asList(
                link("v", "k"), link("v", "d"), link("k", "b"), link("b", "s"), link("d", "s")));
    }
}
