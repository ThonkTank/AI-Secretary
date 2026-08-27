package de.thonktank.autosecretary

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.presentation.alltasks.AllTasksAction
import de.thonktank.autosecretary.presentation.alltasks.AllTasksActionSink
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeHostView
import de.thonktank.autosecretary.presentation.alltasks.AllTasksPresentationState
import de.thonktank.autosecretary.presentation.alltasks.AllTasksSavedStateAdapter
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState
import java.time.LocalTime

/** Debug-only authoritative-state loop for phase-6a semantics and interaction verification. */
class AllTasksComposeHarnessActivity : ComponentActivity(), AllTasksActionSink {
    private companion object { const val STATE_PRESENTATION = "all_tasks_compose_presentation" }
    private val adapter = AllTasksSavedStateAdapter()
    private val catalog = AllTasksComposeFixture.catalog()
    lateinit var allTasks: AllTasksComposeHostView
        private set
    var state: AllTasksUiState = AllTasksComposeFixture.state()
        private set
    var palette: DayPalette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
        private set
    var lastMove: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presentation = savedInstanceState?.getBundle(STATE_PRESENTATION)?.let(adapter::decode)
            ?: AllTasksPresentationState.defaults()
        state = AllTasksUiState.from(catalog, presentation)
        val root = FrameLayout(this)
        root.addView(ForestBackdropView(this).also { it.setPalette(palette) },
            FrameLayout.LayoutParams(-1, -1))
        allTasks = AllTasksComposeHostView(this).also { it.id = R.id.all_tasks_compose_host }
        root.addView(allTasks, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        bind()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(STATE_PRESENTATION, adapter.encode(state.presentation))
        super.onSaveInstanceState(outState)
    }

    fun render(value: AllTasksUiState, palette: DayPalette = this.palette) {
        state = value
        this.palette = palette
        bind()
    }

    override fun emit(action: AllTasksAction) {
        when (action) {
            is AllTasksAction.QueryChanged -> update(state.withQuery(action.value))
            is AllTasksAction.StatusChanged -> update(state.withStatus(action.value))
            is AllTasksAction.SlotsChanged -> update(state.withSlots(action.value))
            is AllTasksAction.RecurrencesChanged -> update(state.withRecurrences(action.value))
            is AllTasksAction.WeekdayChanged -> update(state.withWeekday(action.value))
            is AllTasksAction.ModeChanged -> update(state.withMode(action.value))
            is AllTasksAction.FiltersExpandedChanged ->
                update(state.withFiltersExpanded(action.value))
            is AllTasksAction.ResetFilters -> update(state.resetVisibleFilters())
            is AllTasksAction.CardToggled -> update(state.toggleExpanded(action.cardKey))
            is AllTasksAction.EditTask -> lastMove = "edit-task:${action.taskId.value}"
            is AllTasksAction.EditStep ->
                lastMove = "edit-step:${action.taskId.value}:${action.stepId.value}"
            is AllTasksAction.AddStep -> lastMove = "add-step:${action.taskId.value}"
            is AllTasksAction.DeleteRequested -> lastMove = "delete:${action.taskId.value}"
            is AllTasksAction.ScheduleMoved -> lastMove = with(action.request) {
                "schedule:${entryId.value}:${targetSlot.name}:${beforeEntryId.orElse(null)?.value}"
            }
            is AllTasksAction.StepMoved -> lastMove = with(action.request) {
                "step:${stepId.value}:${targetTaskId.value}:${beforeStepId.orElse(null)?.value}"
            }
            is AllTasksAction.StepsSwapped -> lastMove = with(action.request) {
                "swap:${stepId.value}:${targetStepId.value}"
            }
            is AllTasksAction.RequestAcknowledged,
            is AllTasksAction.DeleteConfirmed -> Unit
            else -> Unit
        }
    }

    private fun update(value: AllTasksUiState) {
        state = value
        bind()
    }

    private fun bind() = allTasks.bind(state, palette, this)
}
