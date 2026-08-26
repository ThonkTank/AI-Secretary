package de.thonktank.autosecretary.presentation.alltasks

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import de.thonktank.autosecretary.DayPalette
import java.time.LocalTime

/**
 * Debug-only Java-friendly comparison boundary for phase 6a.
 *
 * The host owns no presentation state. Inputs become visible only after the existing
 * AllTasksViewModel publishes them again and [bind] receives that authoritative value.
 */
class AllTasksComposeHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    private var screenState by mutableStateOf(AllTasksUiState.empty())
    private var palette by mutableStateOf(DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO))
    private var listener: AllTasksView.Listener? = null
    private var dragSourceKey by mutableStateOf<String?>(null)
    private var forcedOpenFilter by mutableStateOf<AllTasksFilterMenu?>(null)

    init {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
        )
    }

    fun bind(
        state: AllTasksUiState,
        palette: DayPalette,
        listener: AllTasksView.Listener,
    ) {
        this.listener = listener
        this.screenState = state
        this.palette = palette
    }

    fun setDragSourceForTest(key: String?) {
        dragSourceKey = key
    }

    fun openFilterForTest(name: String?) {
        forcedOpenFilter = name?.let(AllTasksFilterMenu::valueOf)
    }

    fun dispatchDropForTest(sourceKey: String, targetKey: String): Boolean =
        AllTasksComposeDispatcher(screenState, callbacks()).drop(sourceKey, targetKey)

    fun dispose() {
        listener = null
        disposeComposition()
    }

    @Composable
    override fun Content() {
        AllTasksComposeScreen(
            state = screenState,
            palette = palette,
            callbacks = callbacks(),
            dragSourceKey = dragSourceKey,
            forcedOpenFilter = forcedOpenFilter,
        )
    }

    private fun callbacks() = AllTasksComposeCallbacks(
        onQuery = { listener?.onQuery(it) },
        onStatus = { listener?.onStatus(it) },
        onSlots = { listener?.onSlots(it) },
        onRecurrences = { listener?.onRecurrences(it) },
        onWeekday = { listener?.onWeekday(it) },
        onMode = { listener?.onMode(it) },
        onFiltersExpanded = { listener?.onFiltersExpanded(it) },
        onResetFilters = { listener?.onResetFilters() },
        onToggleTask = { listener?.onToggleTask(it) },
        onEditTask = { listener?.onEditTask(it) },
        onEditStep = { taskId, stepId -> listener?.onEditStep(taskId, stepId) },
        onAddStep = { listener?.onAddStep(it) },
        onDeleteTask = { taskId, title -> listener?.onDeleteTask(taskId, title) },
        onMoveSchedule = { entryId, slot, before ->
            listener?.onMoveSchedule(entryId, slot, before)
        },
        onMoveStep = { stepId, taskId, before ->
            listener?.onMoveStep(stepId, taskId, before)
        },
        onSwapSteps = { stepId, targetId -> listener?.onSwapSteps(stepId, targetId) },
    )
}
