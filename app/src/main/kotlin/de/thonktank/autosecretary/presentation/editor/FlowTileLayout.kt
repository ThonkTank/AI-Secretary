package de.thonktank.autosecretary.presentation.editor

import de.thonktank.autosecretary.domain.model.FlowTileGraph

internal data class FlowTileSpan(val row: Int, val left: Float, val right: Float)

/** Prerequisites above successors; shared successors span their parents' complete width. */
internal fun tileSpans(graph: FlowTileGraph): Map<String, FlowTileSpan> {
    val result = linkedMapOf<String, FlowTileSpan>()
    val roots = graph.roots()
    graph.topologicalOrder().forEach { id ->
        val parents = graph.predecessors(id)
        var span = if (parents.isEmpty()) {
            val index = roots.indexOf(id)
            FlowTileSpan(0, index.toFloat() / roots.size, (index + 1f) / roots.size)
        } else if (parents.size == 1) {
            val parent = result.getValue(parents.single())
            val siblings = graph.successors(parents.single())
            val index = siblings.indexOf(id)
            val width = (parent.right - parent.left) / siblings.size
            FlowTileSpan(parent.row + 1, parent.left + index * width, parent.left + (index + 1) * width)
        } else FlowTileSpan(parents.maxOf { result.getValue(it).row } + 1,
            parents.minOf { result.getValue(it).left }, parents.maxOf { result.getValue(it).right })
        // Cross-branch joins can span an unrelated lane. Never let their tiles cover another
        // tile; descendants use the shifted row too. No new dependency is inferred from this.
        while (result.values.any { it.row == span.row && it.left < span.right - .00001f && it.right > span.left + .00001f })
            span = span.copy(row = span.row + 1)
        result[id] = span
    }
    return result
}
