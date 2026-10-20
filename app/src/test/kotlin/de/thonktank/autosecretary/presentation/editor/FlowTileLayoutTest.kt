package de.thonktank.autosecretary.presentation.editor

import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Test

class FlowTileLayoutTest {
    @Test fun nestedCrossBranchJoinsNeverCoverOtherTilesAndKeepEveryParentAboveItsChild() {
        val graph = FlowTileGraph(listOf("s", "a", "b", "c", "a2", "b2", "c2", "ac", "tail"),
            listOf("s" to "a", "s" to "b", "s" to "c", "a" to "a2", "b" to "b2", "c" to "c2",
                "a2" to "ac", "c2" to "ac", "b2" to "tail")
                .map { FlowTileGraph.Link(it.first, it.second) })
        val spans = tileSpans(graph)
        graph.links.forEach { assertTrue(spans.getValue(it.source).row < spans.getValue(it.target).row) }
        spans.entries.forEach { (id, a) -> spans.entries.filter { it.key != id }.forEach { (_, b) ->
            assertFalse(a.row == b.row && a.left < b.right - .00001f && a.right > b.left + .00001f)
        } }
        assertEquals(0f, spans.getValue("ac").left, .0001f)
        assertEquals(1f, spans.getValue("ac").right, .0001f)
    }

    @Test fun sharedLaundrySuccessorsSpanAllFourAlternativeStarts() {
        val graph = FlowTileGraph(listOf("b", "w", "t", "l", "h", "a", "p"),
            listOf("b", "w", "t", "l").map { FlowTileGraph.Link(it, "h") } +
                listOf(FlowTileGraph.Link("h", "a"), FlowTileGraph.Link("a", "p")))
        val spans = tileSpans(graph)
        listOf("b", "w", "t", "l").forEachIndexed { index, id ->
            assertEquals(FlowTileSpan(0, index / 4f, (index + 1) / 4f), spans[id])
        }
        listOf("h", "a", "p").forEachIndexed { index, id ->
            assertEquals(FlowTileSpan(index + 1, 0f, 1f), spans[id])
        }
    }

    @Test fun unequalBranchesKeepTheirLanesUntilTheSharedSuccessor() {
        val graph = FlowTileGraph(listOf("v", "k", "d", "b", "s"),
            listOf("v" to "k", "v" to "d", "k" to "b", "b" to "s", "d" to "s")
                .map { FlowTileGraph.Link(it.first, it.second) })
        val spans = tileSpans(graph)
        assertEquals(FlowTileSpan(1, 0f, .5f), spans["k"])
        assertEquals(FlowTileSpan(2, 0f, .5f), spans["b"])
        assertEquals(FlowTileSpan(1, .5f, 1f, endRow = 3), spans["d"])
        assertEquals(FlowTileSpan(3, 0f, 1f), spans["s"])
    }

    @Test fun widthAccountsForNestedParallelSpaceAndShortNeighbourFillsTheStackHeight() {
        val graph = graph("a", "b", "c", "b1", "b2", "d",
            edges = listOf("a" to "b", "a" to "c", "b" to "b1", "b" to "b2",
                "b1" to "d", "b2" to "d", "c" to "d"))
        val spans = tileSpans(graph)
        assertEquals(FlowTileSpan(0, 0f, 1f), spans["a"])
        assertEquals(2f / 3, spans.getValue("b").right, .0001f)
        assertEquals(1f / 3, spans.getValue("b1").right, .0001f)
        assertEquals(2f / 3, spans.getValue("c").left, .0001f)
        assertEquals(3, spans.getValue("c").endRow)
        assertEquals(FlowTileSpan(3, 0f, 1f), spans["d"])
        assertTrue(spans.values.none { it.explicitParents })
    }

    @Test fun narrowingACommonStepExtendsItsNeighbourWithoutChangingThatNeighboursWait() {
        val common = graph("a", "b", "c", "d",
            edges = listOf("a" to "b", "a" to "c", "b" to "d", "c" to "d"))
        val narrow = tileSpans(common.after("d", listOf("b")))
        assertEquals(FlowTileSpan(2, 0f, .5f), narrow["d"])
        assertEquals(FlowTileSpan(1, .5f, 1f, endRow = 3), narrow["c"])
        assertEquals(FlowTileSpan(2, 0f, 1f), tileSpans(common)["d"])
    }

    @Test fun twoOfThreeLanesCanRejoinWhileTheirNeighbourRemainsIndependent() {
        val g = graph("a", "b", "c", "f", "d", "e",
            edges = listOf("a" to "b", "a" to "c", "a" to "f",
                "b" to "d", "c" to "d", "d" to "e", "f" to "e"))
        val spans = tileSpans(g)
        assertTrue(spans.values.none { it.explicitParents })
        assertEquals(0f, spans.getValue("d").left, .0001f)
        assertEquals(2f / 3, spans.getValue("d").right, .0001f)
        assertEquals(spans.getValue("d").endRow, spans.getValue("f").endRow)
        assertEquals(FlowTileSpan(3, 0f, 1f), spans["e"])
    }

    private fun graph(vararg ids: String, edges: List<Pair<String, String>>) =
        FlowTileGraph(ids.toList(), edges.map { FlowTileGraph.Link(it.first, it.second) })
}
