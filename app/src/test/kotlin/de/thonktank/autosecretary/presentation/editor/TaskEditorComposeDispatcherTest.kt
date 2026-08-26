package de.thonktank.autosecretary.presentation.editor

import de.thonktank.autosecretary.EditorStepState
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.TaskEditorValidator
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TaskEditorComposeDispatcherTest {
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun everyDraftTransitionLeavesThroughTheOwnerCallback() {
        val state = validState()
        val recorder = Recorder()
        dispatcher(state, recorder).advance()

        assertEquals(EditorUiState.Page.SCHEDULE, recorder.draft?.page)
        assertNull(recorder.saved)
        assertEquals(0, recorder.dismisses)
    }

    @Test
    fun invalidSaveRoutesToTheFirstFieldWithoutCallingSave() {
        val invalid = validState().draft(
            "", TaskSlot.MORNING, 30, Recurrence.DAILY, 1, 0,
            TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null, null, null,
            "", validState().stepStates, null, 2,
        ).withPage(EditorUiState.Page.SUMMARY, false)
        val recorder = Recorder()

        dispatcher(invalid, recorder).save()

        assertNull(recorder.saved)
        assertEquals(EditorUiState.Page.TITLE, recorder.draft?.page)
        assertEquals(true, recorder.draft?.returnToSummary)
    }

    @Test
    fun promptAndStepBackAreStateTransitionsRatherThanLocalFlags() {
        val dirty = validState()
        val recorder = Recorder()
        dispatcher(dirty, recorder).requestClose()
        assertEquals(EditorUiState.Prompt.DISCARD, recorder.draft?.prompt)

        val detail = dirty.withExpandedStep("step-1").withFeedback(
            dirty.issues,
            EditorUiState.Prompt.NONE,
            dirty.storageError,
        )
        recorder.draft = null
        dispatcher(detail, recorder).back()
        assertNull(recorder.draft?.expandedStepId)
    }

    private fun dispatcher(state: EditorUiState, recorder: Recorder) =
        TaskEditorComposeDispatcher(state, today, recorder.callbacks(), TaskEditorValidator())

    private fun validState(): EditorUiState = EditorUiState.create().draft(
        "Morgenroutine",
        TaskSlot.MORNING,
        30,
        Recurrence.DAILY,
        1,
        0,
        TimeOfDay.MORNING.bit,
        TaskBoundKind.FOREVER,
        null,
        null,
        null,
        null,
        "ruhig beginnen",
        listOf(EditorStepState("step-1", "Dehnen", 0, StepAmount.none(), "")),
        null,
        2,
    )

    private class Recorder {
        var draft: EditorUiState? = null
        var saved: EditorUiState? = null
        var dismisses = 0

        fun callbacks() = TaskEditorComposeCallbacks(
            onDraftChanged = { draft = it },
            onSave = { saved = it },
            onDelete = { },
            onDismiss = { dismisses++ },
        )
    }
}
