package de.thonktank.autosecretary.presentation.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Test

class FlowTileInteractionTest {
    private val graph = FlowTileGraph(listOf("a", "b", "c"), listOf(FlowTileGraph.Link("a", "b")))
    private val boxes = mapOf("a" to Rect(0f, 0f, 100f, 48f), "b" to Rect(0f, 54f, 100f, 102f),
        "c" to Rect(120f, 0f, 220f, 48f))

    @Test fun beforeAfterAndBesideProduceExactlyTheDisplayedProposals() {
        assertEquals(FlowTileGraph.Placement.BEFORE,
            tileDropProposal(graph, "c", FlowTileMove.STEP, Offset(50f, 55f), boxes, 24f)!!.placement)
        assertEquals(FlowTileGraph.Placement.AFTER,
            tileDropProposal(graph, "c", FlowTileMove.STEP, Offset(50f, 100f), boxes, 24f)!!.placement)
        val parallel = tileDropProposal(graph, "c", FlowTileMove.STEP, Offset(99f, 75f), boxes, 24f)!!
        assertEquals(FlowTileGraph.Placement.BESIDE, parallel.placement)
        assertEquals(listOf("a"), parallel.graph.predecessors("c"))
        assertTrue(parallel.graph.successors("c").isEmpty())
        assertEquals(listOf(FlowTileGraph.Link("a", "b")), graph.links)
    }

    @Test fun joinRequiresAnExplicitModeAndHitsOnlyInsideTheSuccessor() {
        val join = tileDropProposal(graph, "c", FlowTileMove.JOIN, Offset(50f, 80f), boxes, 24f)!!
        assertEquals(setOf("a", "c"), join.graph.predecessors("b").toSet())
        assertNull(tileDropProposal(graph, "c", FlowTileMove.JOIN, Offset(105f, 80f), boxes, 24f))
        assertNull(tileDropProposal(graph, "b", FlowTileMove.JOIN, Offset(50f, 20f), boxes, 24f))
    }

    @Test fun aBranchCannotBeDroppedOnItsOwnMembersAndInvalidCoordinatesHaveNoProposal() {
        assertNull(tileDropProposal(graph, "a", FlowTileMove.BRANCH, Offset(50f, 80f), boxes, 0f))
        assertNull(tileDropProposal(graph, "a", FlowTileMove.STEP, Offset(Float.NaN, 80f), boxes, 24f))
        assertNull(tileDropProposal(graph, "a", FlowTileMove.STEP, Offset(1000f, 800f), boxes, 24f))
    }

    @Test fun unchangedOrDuplicateConnectionsAreNotOffered() {
        assertNull(tileProposal(graph, "a", "b", FlowTileMove.JOIN))
        assertNull(tileProposal(graph, "b", "a", FlowTileMove.JOIN))
        assertNull(tileProposal(graph, "a", "a", FlowTileMove.STEP, FlowTileGraph.Placement.BEFORE))
    }

    @Test fun branchGripMovesTheConnectedSequenceButNotItsSharedSuccessor() {
        val fork = FlowTileGraph(listOf("s", "a", "a2", "b", "j", "x"),
            listOf("s" to "a", "s" to "b", "a" to "a2", "a2" to "j", "b" to "j")
                .map { FlowTileGraph.Link(it.first, it.second) })
        val moved = tileProposal(fork, "a", "x", FlowTileMove.BRANCH, FlowTileGraph.Placement.AFTER)!!.graph
        assertEquals(listOf("x"), moved.predecessors("a"))
        assertEquals(listOf("a"), moved.predecessors("a2"))
        assertFalse(moved.predecessors("j").contains("a2"))
        assertEquals(fork.stepIds.toSet(), moved.stepIds.toSet())
    }

    @Test fun scrollVelocityIsBoundedAndStopsOutsideTheEdgeBands() {
        val viewport = Rect(0f, 100f, 320f, 600f)
        assertEquals(-420f, flowEdgeVelocity(90f, viewport, 56f, 420f), .001f)
        assertEquals(0f, flowEdgeVelocity(350f, viewport, 56f, 420f), .001f)
        assertEquals(420f, flowEdgeVelocity(650f, viewport, 56f, 420f), .001f)
        assertEquals(0f, flowEdgeVelocity(Float.NaN, viewport, 56f, 420f), .001f)
        assertEquals(0f, flowEdgeVelocity(1f, Rect.Zero, 56f, 420f), .001f)
    }
}
