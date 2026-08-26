package de.thonktank.autosecretary.presentation.alltasks

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.domain.model.ScheduleEntryId
import de.thonktank.autosecretary.domain.model.TaskId
import de.thonktank.autosecretary.domain.model.TaskStepId
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest
import de.thonktank.autosecretary.domain.steps.StepMoveRequest
import de.thonktank.autosecretary.domain.steps.StepSwapRequest
import java.time.LocalTime
import java.util.Optional

/**
 * The host owns no presentation state. Inputs become visible only after the existing
 * AllTasksViewModel publishes them again and [bind] receives that authoritative value.
 */
class AllTasksComposeHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    private var screenState by mutableStateOf(AllTasksUiState.empty())
    private var palette by mutableStateOf(DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO))
    private var actions: AllTasksActionSink? = null
    private var transientEpoch by mutableIntStateOf(0)
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
        actions: AllTasksActionSink,
    ) {
        this.actions = actions
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

    fun closeTransientState() {
        transientEpoch++
    }

    fun dispose() {
        actions = null
        disposeComposition()
    }

    @Composable
    override fun Content() {
        key(transientEpoch) {
            AllTasksComposeScreen(
                state = screenState,
                palette = palette,
                callbacks = callbacks(),
                dragSourceKey = dragSourceKey,
                forcedOpenFilter = forcedOpenFilter,
            )
        }
    }

    private fun callbacks() = AllTasksComposeCallbacks(
        onQuery = { emit(AllTasksAction.queryChanged(it)) },
        onStatus = { emit(AllTasksAction.statusChanged(it)) },
        onSlots = { emit(AllTasksAction.slotsChanged(it)) },
        onRecurrences = { emit(AllTasksAction.recurrencesChanged(it)) },
        onWeekday = { emit(AllTasksAction.weekdayChanged(it)) },
        onMode = { emit(AllTasksAction.modeChanged(it)) },
        onFiltersExpanded = { emit(AllTasksAction.filtersExpandedChanged(it)) },
        onResetFilters = { emit(AllTasksAction.resetFilters()) },
        onToggleTask = { emit(AllTasksAction.cardToggled(it)) },
        onEditTask = { emit(AllTasksAction.editTask(TaskId.of(it))) },
        onEditStep = { taskId, stepId ->
            emit(AllTasksAction.editStep(TaskId.of(taskId), TaskStepId.of(stepId)))
        },
        onAddStep = { emit(AllTasksAction.addStep(TaskId.of(it))) },
        onDeleteTask = { taskId, title ->
            emit(AllTasksAction.deleteRequested(TaskId.of(taskId), title))
        },
        onMoveSchedule = { entryId, slot, before ->
            emit(AllTasksAction.scheduleMoved(ScheduleMoveRequest(
                ScheduleEntryId.of(entryId),
                slot,
                Optional.ofNullable(before).map(ScheduleEntryId::of),
            )))
        },
        onMoveStep = { stepId, taskId, before ->
            emit(AllTasksAction.stepMoved(StepMoveRequest(
                TaskStepId.of(stepId),
                TaskId.of(taskId),
                Optional.ofNullable(before).map(TaskStepId::of),
            )))
        },
        onSwapSteps = { stepId, targetId ->
            emit(AllTasksAction.stepsSwapped(StepSwapRequest(
                TaskStepId.of(stepId),
                TaskStepId.of(targetId),
            )))
        },
    )

    private fun emit(action: AllTasksAction) {
        actions?.emit(action)
    }
}
