package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val rows = remember(state) { AllTasksRow.project(state) }
    val dispatcher = remember(state, callbacks) { AllTasksComposeDispatcher(state, callbacks) }
    val pageStart = dimensionResource(R.dimen.page_start)
    val pageEnd = dimensionResource(R.dimen.page_end)
    val visibleFilter = forcedOpenFilter ?: openFilter

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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("all-tasks:list"),
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
                    AllTasksComposeRow(
                        row = row,
                        state = state,
                        palette = palette,
                        dispatcher = dispatcher,
                        callbacks = callbacks,
                        dragActive = dragSourceKey != null,
                        selectedSwapStep = selectedSwapStep,
                        onSelectSwap = { selectedSwapStep = it },
                        onOpenTaskMenu = { openTaskMenu = it },
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
