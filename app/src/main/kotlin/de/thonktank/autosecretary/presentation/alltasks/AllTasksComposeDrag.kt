package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import de.thonktank.autosecretary.PresentationTrace

internal fun Modifier.allTasksDragSource(
    key: String,
    enabled: Boolean,
    onStart: (String, Float) -> Unit,
    onMove: (Float) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coordinates = it }
        .pointerInput(key, enabled) {
            if (!enabled) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    onStart(key, coordinates.rootY(local))
                },
                onDrag = { change, _ ->
                    change.consume()
                    onMove(coordinates.rootY(change.position))
                },
                onDragEnd = onDrop,
                onDragCancel = onCancel,
            )
        }
}

internal fun nearestDropTarget(
    pointerY: Float,
    sourceKey: String,
    visibleBounds: Map<String, Rect>,
): String? {
    if (!pointerY.isFinite()) return null
    val candidates = visibleBounds.filterKeys { it != sourceKey }
    val containing = candidates.entries
        .filter { pointerY >= it.value.top && pointerY <= it.value.bottom }
        .minByOrNull { kotlin.math.abs(pointerY - it.value.center.y) }
    return (containing ?: candidates.entries.minByOrNull {
        kotlin.math.abs(pointerY - it.value.center.y)
    })?.key
}

internal fun edgeScrollVelocity(
    pointerY: Float,
    bounds: Rect,
    edgeSizePx: Float,
    maximumPixelsPerSecond: Float,
): Float {
    if (!pointerY.isFinite() || bounds.height <= 0f || edgeSizePx <= 0f) return 0f
    val relative = pointerY - bounds.top
    return when {
        relative < edgeSizePx ->
            -maximumPixelsPerSecond *
                ((edgeSizePx - relative) / edgeSizePx).coerceIn(0f, 1f)
        relative > bounds.height - edgeSizePx ->
            maximumPixelsPerSecond *
                ((relative - (bounds.height - edgeSizePx)) / edgeSizePx).coerceIn(0f, 1f)
        else -> 0f
    }
}

private fun LayoutCoordinates?.rootY(local: Offset): Float {
    val value = this ?: return local.y
    return value.localToRoot(local).y
}

internal fun traceAllTasksDrag(kind: String, detail: String) {
    if (PresentationTrace.enabled()) {
        PresentationTrace.emit("all-tasks-drag", kind, detail)
    }
}
