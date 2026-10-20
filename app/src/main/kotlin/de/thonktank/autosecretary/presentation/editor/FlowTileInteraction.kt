package de.thonktank.autosecretary.presentation.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import kotlin.math.abs

internal enum class FlowTileMove { STEP, BRANCH, JOIN, RESIZE_LEFT, RESIZE_RIGHT }

internal data class FlowTileProposal(
    val source: String, val target: String, val mode: FlowTileMove,
    val placement: FlowTileGraph.Placement?, val graph: FlowTileGraph,
)

internal fun tileProposal(graph: FlowTileGraph, source: String, target: String,
                         mode: FlowTileMove, placement: FlowTileGraph.Placement? = null): FlowTileProposal? {
    if (source == target) return null
    val next = runCatching {
        if (mode == FlowTileMove.JOIN) graph.join(listOf(source), target)
        else graph.place(source, target, requireNotNull(placement), mode == FlowTileMove.BRANCH)
    }.getOrNull() ?: return null
    if (next.stepIds == graph.stepIds && next.links.toSet() == graph.links.toSet()) return null
    return FlowTileProposal(source, target, mode, placement, next)
}

/** The lane ends immediately above a tile, including a taller independent neighbour. */
internal fun tileFrontier(graph: FlowTileGraph, source: String,
                          spans: Map<String, FlowTileSpan> = tileSpans(graph)): List<String> {
    val row = spans.getValue(source).row
    if (spans.getValue(source).explicitParents) return emptyList()
    return spans.filter { (id, span) ->
        id != source && span.row < row && span.endRow >= row && !graph.reaches(source, id)
    }.entries.sortedBy { it.value.left }.map { it.key }
}

internal fun tileWidthProposal(graph: FlowTileGraph, source: String, parents: List<String>): FlowTileProposal? {
    val next = runCatching { graph.after(source, parents) }.getOrNull() ?: return null
    if (next.links.toSet() == graph.links.toSet()) return null
    return FlowTileProposal(source, source, FlowTileMove.RESIZE_RIGHT, null, next)
}

internal fun tileResizeProposal(graph: FlowTileGraph, source: String, mode: FlowTileMove,
                                x: Float, boardWidth: Float): FlowTileProposal? {
    if (!x.isFinite() || boardWidth <= 0f) return null
    val spans = tileSpans(graph)
    val span = spans.getValue(source)
    val boundary = (x / boardWidth).coerceIn(0f, 1f)
    val left = if (mode == FlowTileMove.RESIZE_LEFT) boundary else span.left
    val right = if (mode == FlowTileMove.RESIZE_RIGHT) boundary else span.right
    val parents = tileFrontier(graph, source).filter {
        val candidate = spans.getValue(it)
        (candidate.left + candidate.right) / 2 in left..right
    }
    return tileWidthProposal(graph, source, parents)
}

internal data class FlowJoinLanding(val parents: List<String>, val box: Rect)

/** A seam below adjacent lanes is an explicit shared landing, even with unequal branch lengths. */
internal fun tileJoinLandings(graph: FlowTileGraph, source: String, boxes: Map<String, Rect>,
                             margin: Float): List<FlowJoinLanding> {
    val spans = tileSpans(graph)
    if (spans.values.any { it.explicitParents }) return emptyList()
    val results = mutableListOf<FlowJoinLanding>()
    for (row in 1..(spans.values.maxOfOrNull { it.endRow } ?: 0)) {
        val frontier = spans.filter { (id, span) ->
            id in boxes && !graph.reaches(source, id) && span.row < row && span.endRow >= row
        }.entries.sortedBy { it.value.left }
        for (start in frontier.indices) for (end in start + 1..frontier.lastIndex) {
            val lanes = frontier.subList(start, end + 1)
            if (lanes.zipWithNext().any { (a, b) -> abs(a.value.right - b.value.left) > .001f }) continue
            val parents = lanes.map { it.key }
            val left = boxes.getValue(parents.first()).left
            val right = boxes.getValue(parents.last()).right
            val bottom = parents.maxOf { boxes.getValue(it).bottom }
            val center = (left + right) / 2
            val half = minOf(margin, (right - left) / 6)
            results.add(FlowJoinLanding(parents, Rect(center - half, bottom,
                center + half, bottom + margin)))
        }
    }
    return results.distinctBy { it.parents }
}

/** Hit testing stays on the pre-gesture geometry. Preview reflow cannot change the chosen target. */
internal fun tileDropProposal(graph: FlowTileGraph, source: String, mode: FlowTileMove,
                             point: Offset, boxes: Map<String, Rect>, margin: Float): FlowTileProposal? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    if (mode == FlowTileMove.RESIZE_LEFT || mode == FlowTileMove.RESIZE_RIGHT)
        return tileResizeProposal(graph, source, mode, point.x, boxes.values.maxOfOrNull { it.right } ?: 0f)
    if (mode == FlowTileMove.STEP) {
        val shared = tileJoinLandings(graph, source, boxes, margin)
            .filter { it.box.contains(point) }.minByOrNull { abs(it.box.center.x - point.x) }
        if (shared != null) {
            val next = runCatching {
                graph.place(source, shared.parents.first(), FlowTileGraph.Placement.AFTER, false)
                    .after(source, shared.parents)
            }.getOrNull()
            if (next != null && (next.links.toSet() != graph.links.toSet() || next.stepIds != graph.stepIds))
                return FlowTileProposal(source, shared.parents.first(), mode, FlowTileGraph.Placement.AFTER, next)
        }
    }
    val moved = if (mode == FlowTileMove.BRANCH) graph.branchFrom(source) else setOf(source)
    val targets = boxes.filterKeys { it !in moved }
    val target = targets.entries.filter { (_, box) ->
        val pad = if (mode == FlowTileMove.JOIN) 0f else margin
        point.x in (box.left - pad)..(box.right + pad) && point.y in (box.top - pad)..(box.bottom + pad)
    }.minByOrNull { (_, box) ->
        val dx = (point.x - box.center.x) / box.width.coerceAtLeast(1f)
        val dy = (point.y - box.center.y) / box.height.coerceAtLeast(1f)
        dx * dx + dy * dy
    } ?: return null
    if (mode == FlowTileMove.JOIN) return tileProposal(graph, source, target.key, mode)
    val box = target.value
    val beside = abs(point.x - box.center.x) > box.width * .3f &&
        abs(point.y - box.center.y) < box.height * .65f
    val placement = if (beside) {
        if (point.x < box.center.x) FlowTileGraph.Placement.LEFT else FlowTileGraph.Placement.RIGHT
    }
        else if (point.y < box.center.y) FlowTileGraph.Placement.BEFORE else FlowTileGraph.Placement.AFTER
    return tileProposal(graph, source, target.key, mode, placement)
}

internal fun flowEdgeVelocity(y: Float, viewport: Rect, edge: Float, speed: Float): Float {
    if (!y.isFinite() || viewport.height <= 0f || edge <= 0f) return 0f
    val band = edge.coerceAtMost(viewport.height / 2)
    return when {
        y < viewport.top + band -> -speed * ((viewport.top + band - y) / band).coerceIn(0f, 1f)
        y > viewport.bottom - band -> speed * ((y - viewport.bottom + band) / band).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** A tap remains a tap; only crossing touch slop consumes the gesture, for mouse and touch alike. */
internal fun Modifier.flowTileGrip(graph: FlowTileGraph, onStart: (Offset) -> Unit,
                                 onMove: (Offset) -> Unit, onDrop: () -> Unit,
                                 onCancel: () -> Unit): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Local function references can compare equal even when they capture a new
    // graph's gesture state. Structural equality retained the old move/drop/cancel
    // closures after editing or undo, while start already wrote the new state.
    val start by remember { mutableStateOf(onStart, referentialEqualityPolicy()) }.apply { value = onStart }
    val move by remember { mutableStateOf(onMove, referentialEqualityPolicy()) }.apply { value = onMove }
    val drop by remember { mutableStateOf(onDrop, referentialEqualityPolicy()) }.apply { value = onDrop }
    val cancel by remember { mutableStateOf(onCancel, referentialEqualityPolicy()) }.apply { value = onCancel }
    onGloballyPositioned { coordinates = it }.pointerInput(graph) {
        try {
            detectDragGestures(
                onDragStart = { start(coordinates?.localToRoot(it) ?: it) },
                onDragEnd = { drop() }, onDragCancel = { cancel() },
                onDrag = { change, _ ->
                    change.consume()
                    move(coordinates?.localToRoot(change.position) ?: change.position)
                },
            )
        } finally { cancel() }
    }
}
