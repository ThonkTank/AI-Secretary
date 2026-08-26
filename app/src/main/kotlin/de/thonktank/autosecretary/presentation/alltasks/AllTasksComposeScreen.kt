package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R

@Composable
internal fun AllTasksComposeScreen(
    state: AllTasksUiState,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    modifier: Modifier = Modifier,
    dragSourceKey: String? = null,
    forcedOpenFilter: AllTasksFilterMenu? = null,
) {
    var openFilter by remember { mutableStateOf<AllTasksFilterMenu?>(null) }
    var openTaskMenu by remember { mutableStateOf<String?>(null) }
    var selectedSwapStep by remember { mutableStateOf<String?>(null) }
    var activeDragKey by remember { mutableStateOf<String?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(Float.NaN) }
    var listBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val rowBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val listState = rememberLazyListState()
    val rows = remember(state) { AllTasksRow.project(state) }
    val dispatcher = remember(state, callbacks) { AllTasksComposeDispatcher(state, callbacks) }
    val pageStart = dimensionResource(R.dimen.page_start)
    val pageEnd = dimensionResource(R.dimen.page_end)
    val visibleFilter = forcedOpenFilter ?: openFilter
    val visibleDragKey = dragSourceKey ?: activeDragKey
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 64.dp.toPx() }
    val edgeSpeedPx = with(density) { 460.dp.toPx() }

    fun clearDrag() {
        activeDragKey = null
        dragPointerY = Float.NaN
    }

    LaunchedEffect(activeDragKey) {
        if (activeDragKey == null) return@LaunchedEffect
        var previous = androidx.compose.runtime.withFrameNanos { it }
        var previousDirection = 0
        try {
            while (activeDragKey != null) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val elapsedSeconds =
                    ((now - previous).coerceAtMost(100_000_000L)) / 1_000_000_000f
                previous = now
                val velocity = edgeScrollVelocity(
                    dragPointerY,
                    listBounds,
                    edgeSizePx,
                    edgeSpeedPx,
                )
                val direction = when {
                    velocity < 0f -> -1
                    velocity > 0f -> 1
                    else -> 0
                }
                if (direction != previousDirection) {
                    traceAllTasksDrag("edge", "direction=$direction")
                    previousDirection = direction
                }
                if (velocity != 0f) listState.scrollBy(velocity * elapsedSeconds)
            }
        } finally {
            if (previousDirection != 0) traceAllTasksDrag("edge", "direction=0")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("all-tasks:compose")
            .padding(start = pageStart, top = 16.dp, end = pageEnd),
    ) {
        Column(Modifier.fillMaxSize()) {
            AllTasksComposeControls(
                state = state,
                palette = palette,
                callbacks = callbacks,
                onOpenMenu = { openFilter = if (openFilter == it) null else it },
                onCloseMenu = { openFilter = null },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("all-tasks:list")
                    .onGloballyPositioned { listBounds = it.boundsInRoot() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 10.dp,
                    bottom = 26.dp,
                ),
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
                        dragActive = visibleDragKey?.let { key ->
                            rows.firstOrNull { it.key == key }?.kind == AllTasksRow.Kind.STEP
                        } == true,
                        selectedSwapStep = selectedSwapStep,
                        onSelectSwap = { selectedSwapStep = it },
                        onOpenTaskMenu = { openTaskMenu = it },
                        rowModifier = Modifier
                            .onGloballyPositioned { rowBounds[row.key] = it.boundsInRoot() }
                            .allTasksDragSource(
                                key = row.key,
                                enabled = row.kind == AllTasksRow.Kind.SCHEDULE ||
                                    row.kind == AllTasksRow.Kind.STEP && !row.task.archived,
                                onStart = { key, pointerY ->
                                    activeDragKey = key
                                    dragPointerY = pointerY
                                    traceAllTasksDrag("start", "source=$key")
                                    openFilter = null
                                    openTaskMenu = null
                                },
                                onMove = { dragPointerY = it },
                                onDrop = {
                                    val source = activeDragKey
                                    if (source != null) {
                                        val target = nearestDropTarget(
                                            dragPointerY,
                                            source,
                                            rowBounds,
                                        )
                                        val handled = target?.let { dispatcher.drop(source, it) }
                                            ?: false
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
                    )
                }
            }
        }

        if (visibleFilter != null || openTaskMenu != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("all-tasks:overlay")
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        openFilter = null
                        openTaskMenu = null
                    },
            )
        }
        visibleFilter?.let { menu ->
            AllTasksComposeFilterDropdown(
                state = state,
                palette = palette,
                menu = menu,
                callbacks = callbacks,
                onClose = { openFilter = null },
                modifier = Modifier.fillMaxWidth().padding(top = 108.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(top = 164.dp, start = 88.dp),
                )
            }
        }
    }
}
