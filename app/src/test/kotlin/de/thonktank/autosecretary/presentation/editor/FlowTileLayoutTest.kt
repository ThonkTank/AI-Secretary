package de.thonktank.autosecretary.presentation.editor

import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Test

class FlowTileLayoutTest {
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
        assertEquals(FlowTileSpan(1, .5f, 1f), spans["d"])
        assertEquals(FlowTileSpan(3, 0f, 1f), spans["s"])
    }
}
