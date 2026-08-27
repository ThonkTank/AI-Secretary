package de.thonktank.autosecretary.presentation.alltasks

import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllTasksComposeDispatcherTest {
    @Test
    fun stableKeysMapStepAndScheduleDropsToTheExistingCommands() {
        val recorder = Recorder()
        val list = AllTasksComposeFixture.state().toggleExpanded(
            AllTasksUiState.cardKey("morning", TaskSlot.MORNING),
        ).toggleExpanded(
            AllTasksUiState.cardKey("bed", TaskSlot.MORNING),
        )
        val dispatcher = AllTasksComposeDispatcher(list, recorder.callbacks)

        assertTrue(dispatcher.drop(
            "step:morning|MORNING:morning-step-0",
            "STEP_TARGET:bed|MORNING:end",
        ))
        assertEquals("step:morning-step-0:bed:null", recorder.last)
        assertTrue(dispatcher.drop(
            "step:morning|MORNING:morning-step-0",
            "step:morning|MORNING:morning-step-1",
        ))
        assertEquals("swap:morning-step-0:morning-step-1", recorder.last)

        val sort = AllTasksComposeDispatcher(
            AllTasksComposeFixture.state().withMode(AllTasksUiState.Mode.SORT),
            recorder.callbacks,
        )
        assertTrue(sort.drop("schedule:morning-MORNING", "SCHEDULE_TARGET:EVENING:end"))
        assertEquals("schedule:morning-MORNING:EVENING:null", recorder.last)
        assertFalse(sort.drop("slot:MORNING", "slot:MIDDAY"))
    }

    @Test
    fun accessibilityMappingsKeepTheEstablishedBeforeIdRules() {
        val recorder = Recorder()
        val state = AllTasksComposeFixture.state().toggleExpanded(
            AllTasksUiState.cardKey("morning", TaskSlot.MORNING),
        )
        val rows = AllTasksRow.project(state)
        val dispatcher = AllTasksComposeDispatcher(state, recorder.callbacks)
        val first = rows.first { it.kind == AllTasksRow.Kind.STEP &&
            it.step.id == "morning-step-0" }
        assertFalse(dispatcher.canMoveStepBy(first, -1))
        assertTrue(dispatcher.canMoveStepBy(first, 1))
        assertTrue(dispatcher.canMoveStepToTask(first, 1))
        val second = rows.first { it.kind == AllTasksRow.Kind.STEP &&
            it.step.id == "morning-step-1" }
        assertTrue(dispatcher.moveStepBy(second, -1))
        assertEquals("step:morning-step-1:morning:morning-step-0", recorder.last)
        assertTrue(dispatcher.moveStepToTask(second, 1))
        assertEquals("step:morning-step-1:bed:null", recorder.last)

        val sortState = AllTasksComposeFixture.state().withMode(AllTasksUiState.Mode.SORT)
        val schedule = AllTasksRow.project(sortState).first {
            it.kind == AllTasksRow.Kind.SCHEDULE && it.schedule.id == "morning-MORNING"
        }
        val sort = AllTasksComposeDispatcher(sortState, recorder.callbacks)
        assertTrue(sort.moveScheduleToSlot(schedule, 1))
        assertEquals("schedule:morning-MORNING:MIDDAY:null", recorder.last)
    }

    private class Recorder {
        var last: String? = null
        val callbacks = AllTasksComposeCallbacks(
            onQuery = {}, onStatus = {}, onSlots = {}, onRecurrences = {}, onWeekday = {},
            onMode = {}, onFiltersExpanded = {}, onResetFilters = {}, onToggleTask = {},
            onEditTask = {}, onEditStep = { _, _ -> }, onAddStep = {},
            onDeleteTask = { _, _ -> },
            onMoveSchedule = { entry, slot, before ->
                last = "schedule:$entry:${slot.name}:$before"
            },
            onMoveStep = { step, task, before -> last = "step:$step:$task:$before" },
            onSwapSteps = { first, second -> last = "swap:$first:$second" },
        )
    }
}
