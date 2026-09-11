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

internal enum class FlowTileMove { STEP, BRANCH, JOIN }

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

/** Hit testing stays on the pre-gesture geometry. Preview reflow cannot change the chosen target. */
internal fun tileDropProposal(graph: FlowTileGraph, source: String, mode: FlowTileMove,
                             point: Offset, boxes: Map<String, Rect>, margin: Float): FlowTileProposal? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
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
    val placement = if (beside) FlowTileGraph.Placement.BESIDE
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
