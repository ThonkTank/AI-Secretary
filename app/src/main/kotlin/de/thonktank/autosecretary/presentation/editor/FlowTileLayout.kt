package de.thonktank.autosecretary.presentation.editor

import de.thonktank.autosecretary.domain.model.FlowTileGraph

internal data class FlowTileSpan(
    val row: Int, val left: Float, val right: Float,
    val endRow: Int = row + 1, val explicitParents: Boolean = false,
)

/**
 * Sequence/parallel regions share a single layout contract. A short lane fills the height up
 * to its sibling's end; a fork reserves the width of its widest parallel section, not its
 * number of sequential steps. Neither dimension is a duration or an execution cursor.
 */
internal fun tileSpans(graph: FlowTileGraph): Map<String, FlowTileSpan> = try {
    StackLayout(graph).layout()
} catch (_: IllegalStateException) {
    explicitGraphSpans(graph)
}

private class StackLayout(private val graph: FlowTileGraph) {
    private val order = graph.topologicalOrder()
    private val result = linkedMapOf<String, FlowTileSpan>()
    private val widths = mutableMapOf<Pair<String, String?>, Int>()
    private fun children(id: String) = graph.stepIds.filter { it in graph.successors(id) }

    private fun join(ids: List<String>, stop: String?): String? =
        if (ids.size < 2) stop else order.firstOrNull { candidate ->
            ids.all { it != candidate && graph.reaches(it, candidate) } &&
                (stop == null || graph.reaches(candidate, stop))
        } ?: stop

    private fun width(id: String, stop: String?): Int {
        if (id == stop) return 0
        return widths.getOrPut(id to stop) {
            maxOf(1, groupWidth(children(id).filter { it != stop }, stop))
        }
    }

    /**
     * Adjacent lanes may merge before the whole parallel region does. Keep those lanes
     * together as a nested region. Overlapping/non-adjacent joins are explicit legacy DAGs.
     */
    private fun partitions(ids: List<String>, merge: String?): List<List<String>> {
        val owner = IntArray(ids.size) { it }
        fun root(index: Int): Int = if (owner[index] == index) index else root(owner[index])
        order.filter { it != merge && (merge == null || graph.reaches(it, merge)) }.forEach { candidate ->
            val members = ids.indices.filter { graph.reaches(ids[it], candidate) }
            if (members.size in 2 until ids.size) {
                check(members.last() - members.first() + 1 == members.size)
                val first = root(members.first())
                members.forEach { owner[root(it)] = first }
            }
        }
        val groups = ids.indices.groupBy { root(it) }.values.map { members -> members.map { ids[it] } }
        check(groups.size > 1) { "Overlapping partial joins" }
        return groups
    }

    private fun groupWidth(ids: List<String>, stop: String?): Int {
        if (ids.isEmpty()) return 0
        if (ids.size == 1) return width(ids.single(), stop)
        val merge = join(ids, stop)
        return maxOf(partitions(ids, merge).sumOf { groupWidth(it, merge) },
            if (merge != null && merge != stop) width(merge, stop) else 0)
    }

    private data class End(val row: Int, val tails: List<String>)

    private fun sequence(id: String, stop: String?, row: Int, left: Float, right: Float): End {
        if (id == stop) return End(row, emptyList())
        check(id !in result) { "Cross-connected legacy graph" }
        result[id] = FlowTileSpan(row, left, right)
        val next = children(id)
        if (next.isEmpty() || next == listOf(stop)) return End(row + 1, listOf(id))
        check(stop !in next) { "A bypass cannot be represented by a rectangular stack" }
        return group(next, stop, row + 1, left, right)
    }

    private fun group(ids: List<String>, stop: String?, row: Int, left: Float, right: Float): End {
        if (ids.size == 1) return sequence(ids.single(), stop, row, left, right)
        check(ids.none { a -> ids.any { b -> a != b && graph.reaches(a, b) } })
        val merge = join(ids, stop)
        val groups = partitions(ids, merge)
        val weights = groups.map { groupWidth(it, merge) }
        val total = weights.sum().toFloat()
        var cursor = left
        val ends = groups.mapIndexed { index, lanes ->
            val end = if (index == groups.lastIndex) right else cursor + (right - left) * weights[index] / total
            group(lanes, merge, row, cursor, end).also { cursor = end }
        }
        val bottom = ends.maxOf { it.row }
        ends.forEach { end -> end.tails.forEach { id ->
            result[id] = result.getValue(id).copy(endRow = bottom)
        } }
        return if (merge != null && merge != stop) sequence(merge, stop, bottom, left, right)
        else End(bottom, ends.flatMap { it.tails })
    }

    fun layout(): Map<String, FlowTileSpan> {
        if (graph.stepIds.isEmpty()) return emptyMap()
        group(graph.roots(), null, 0, 0f, 1f)
        check(result.size == graph.stepIds.size)
        return result
    }
}

/** Existing cross-connected DAGs keep their edges and explicitly label their prerequisites. */
private fun explicitGraphSpans(graph: FlowTileGraph): Map<String, FlowTileSpan> {
    val result = linkedMapOf<String, FlowTileSpan>()
    val roots = graph.roots()
    graph.topologicalOrder().forEach { id ->
        val parents = graph.predecessors(id)
        var span = if (parents.isEmpty()) {
            val index = roots.indexOf(id)
            FlowTileSpan(0, index.toFloat() / roots.size, (index + 1f) / roots.size)
        } else if (parents.size == 1) {
            val parent = result.getValue(parents.single())
            val siblings = graph.stepIds.filter { it in graph.successors(parents.single()) }
            val index = siblings.indexOf(id)
            val width = (parent.right - parent.left) / siblings.size
            FlowTileSpan(parent.endRow, parent.left + index * width, parent.left + (index + 1) * width)
        } else FlowTileSpan(parents.maxOf { result.getValue(it).endRow },
            parents.minOf { result.getValue(it).left }, parents.maxOf { result.getValue(it).right })
        while (result.values.any { it.row == span.row && it.left < span.right - .00001f && it.right > span.left + .00001f })
            span = span.copy(row = span.row + 1, endRow = span.endRow + 1)
        result[id] = span.copy(explicitParents = true)
    }
    return result
}
