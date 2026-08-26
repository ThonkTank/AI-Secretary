package de.thonktank.autosecretary

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeHostView
import de.thonktank.autosecretary.presentation.alltasks.AllTasksPresentationState
import de.thonktank.autosecretary.presentation.alltasks.AllTasksSavedStateAdapter
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState
import de.thonktank.autosecretary.presentation.alltasks.AllTasksView
import java.time.LocalTime

/** Debug-only authoritative-state loop for phase-6a semantics and interaction verification. */
class AllTasksComposeHarnessActivity : ComponentActivity(), AllTasksView.Listener {
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

    override fun onQuery(query: String) = update(state.withQuery(query))
    override fun onStatus(status: AllTasksUiState.Status) = update(state.withStatus(status))
    override fun onSlots(slots: Set<TaskSlot>) = update(state.withSlots(slots))
    override fun onRecurrences(recurrences: Set<Recurrence>) =
        update(state.withRecurrences(recurrences))
    override fun onWeekday(weekday: Int) = update(state.withWeekday(weekday))
    override fun onMode(mode: AllTasksUiState.Mode) = update(state.withMode(mode))
    override fun onFiltersExpanded(expanded: Boolean) = update(state.withFiltersExpanded(expanded))
    override fun onResetFilters() = update(state.resetVisibleFilters())
    override fun onToggleTask(cardKey: String) = update(state.toggleExpanded(cardKey))
    override fun onEditTask(taskId: String) { lastMove = "edit-task:$taskId" }
    override fun onEditStep(taskId: String, stepId: String) {
        lastMove = "edit-step:$taskId:$stepId"
    }
    override fun onAddStep(taskId: String) { lastMove = "add-step:$taskId" }
    override fun onDeleteTask(taskId: String, title: String) { lastMove = "delete:$taskId" }
    override fun onMoveSchedule(entryId: String, slot: TaskSlot, beforeEntryId: String?) {
        lastMove = "schedule:$entryId:${slot.name}:$beforeEntryId"
    }
    override fun onMoveStep(stepId: String, taskId: String, beforeStepId: String?) {
        lastMove = "step:$stepId:$taskId:$beforeStepId"
    }
    override fun onSwapSteps(stepId: String, targetStepId: String) {
        lastMove = "swap:$stepId:$targetStepId"
    }

    private fun update(value: AllTasksUiState) {
        state = value
        bind()
    }

    private fun bind() = allTasks.bind(state, palette, this)
}
