package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.domain.model.FlowRunSummary

@Composable
internal fun AllTasksComposeScreen(
    state: AllTasksUiState,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    modifier: Modifier = Modifier,
    dragSourceKey: String? = null,
    forcedFilterSheet: Boolean = false,
    flowRuns: List<FlowRunSummary> = emptyList(),
    nowEpochMillis: Long = 0L,
    onOpenFlowRuns: () -> Unit = { },
) {
    var filterSheetOpen by remember { mutableStateOf(false) }
    var openTaskMenu by remember { mutableStateOf<String?>(null) }
    var selectedSwapStep by remember { mutableStateOf<String?>(null) }
    var activeDragKey by remember { mutableStateOf<String?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(Float.NaN) }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    val listState = rememberLazyListState()
    val rows = remember(state) { AllTasksRow.project(state) }
    val draggableRowKeys = remember(rows) {
        rows.asSequence()
            .filter { row ->
                row.kind == AllTasksRow.Kind.SCHEDULE ||
                    row.kind == AllTasksRow.Kind.STEP && !row.task.archived
            }
            .mapTo(mutableSetOf()) { it.key }
    }
    val dispatcher = remember(state, callbacks) { AllTasksComposeDispatcher(state, callbacks) }
    val visibleDragKey = dragSourceKey ?: activeDragKey
    val activeDropKey = if (visibleDragKey != null && dragPointerY.isFinite()) {
        nearestDropTarget(dragPointerY, visibleDragKey, rowBounds)
    } else null
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 64.dp.toPx() }
    val edgeSpeedPx = with(density) { 460.dp.toPx() }

    fun clearDrag() {
        activeDragKey = null
        dragPointerY = Float.NaN
    }

    LaunchedEffect(activeDragKey, dragPointerY, listBounds) {
        if (activeDragKey == null) return@LaunchedEffect
        val velocity = edgeScrollVelocity(dragPointerY, listBounds, edgeSizePx, edgeSpeedPx)
        if (velocity == 0f) return@LaunchedEffect
        var previous = androidx.compose.runtime.withFrameNanos { it }
        val direction = if (velocity < 0f) -1 else 1
        traceAllTasksDrag("edge", "direction=$direction")
        try {
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val elapsedSeconds =
                    ((now - previous).coerceAtMost(100_000_000L)) / 1_000_000_000f
                previous = now
                listState.scrollBy(velocity * elapsedSeconds)
            }
        } finally {
            traceAllTasksDrag("edge", "direction=0")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color(palette.background).copy(alpha = .96f))
            .testTag("all-tasks:compose"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, top = 14.dp, end = 16.dp),
        ) {
            AllTasksSearch(
                query = state.query,
                palette = palette,
                onQuery = callbacks.onQuery,
                modifier = Modifier.fillMaxWidth().testTag("all-tasks:search"),
            )
            if (state.mode == AllTasksUiState.Mode.LIST) {
                AllTasksRunningFlows(flowRuns, palette, nowEpochMillis, onOpenFlowRuns)
            }
            AllTasksComposeControls(
                state = state,
                palette = palette,
                callbacks = callbacks,
                onOpenFilters = {
                    filterSheetOpen = true
                    openTaskMenu = null
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("all-tasks:list")
                    .onGloballyPositioned { listBounds = it.boundsInRoot() }
                    .allTasksDragContainer(
                        enabledKeys = draggableRowKeys,
                        visibleBounds = rowBounds,
                        onStart = { key, pointerY ->
                            activeDragKey = key
                            dragPointerY = pointerY
                            traceAllTasksDrag("start", "source=$key")
                            filterSheetOpen = false
                            openTaskMenu = null
                        },
                        onMove = { dragPointerY = it },
                        onDrop = {
                            val source = activeDragKey
                            if (source != null) {
                                val target = nearestDropTarget(dragPointerY, source, rowBounds)
                                val handled = target?.let { dispatcher.drop(source, it) } ?: false
                                traceAllTasksDrag(
                                    "drop",
                                    "source=$source target=$target handled=$handled",
                                )
                            }
                            clearDrag()
                        },
                        onCancel = {
                            traceAllTasksDrag("cancel", "source=$activeDragKey")
                            clearDrag()
                        },
                    ),
                contentPadding = PaddingValues(top = 4.dp, bottom = 26.dp),
            ) {
                items(
                    items = rows,
                    key = { row -> row.key },
                    contentType = { row -> row.kind },
                ) { row ->
                    DisposableEffect(row.key) {
                        onDispose { rowBounds.remove(row.key) }
                    }
                    AllTasksComposeRow(
                        row = row,
                        state = state,
                        palette = palette,
                        dispatcher = dispatcher,
                        callbacks = callbacks,
                        dropIndicator = activeDropKey == row.key,
                        selectedSwapStep = selectedSwapStep,
                        onSelectSwap = { selectedSwapStep = it },
                        onOpenTaskMenu = { openTaskMenu = it },
                        rowModifier = Modifier.onGloballyPositioned {
                            rowBounds[row.key] = it.boundsInRoot()
                        },
                    )
                }
            }
        }

        if (filterSheetOpen || forcedFilterSheet || openTaskMenu != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("all-tasks:overlay")
                    .background(color(palette.ink).copy(alpha = .18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        filterSheetOpen = false
                        openTaskMenu = null
                    },
            )
        }
        if (filterSheetOpen || forcedFilterSheet) {
            AllTasksComposeFilterSheet(
                state = state,
                palette = palette,
                callbacks = callbacks,
                onClose = { filterSheetOpen = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        openTaskMenu?.let { taskId ->
            val task = state.tasks.firstOrNull { it.task.id.value == taskId }
            if (task != null) {
                AllTasksComposeTaskMenu(
                    item = task,
                    palette = palette,
                    callbacks = callbacks,
                    onClose = { openTaskMenu = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(top = 168.dp, end = 16.dp),
                )
            }
        }
    }
}
