package de.thonktank.autosecretary

import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay
import de.thonktank.autosecretary.presentation.editor.TaskEditorComposeCallbacks
import de.thonktank.autosecretary.presentation.editor.TaskEditorComposeDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskEditorComposeDispatcherTest {
    @Test
    fun createJourneyVisitsEveryPageAndSavesThroughTheOwnerCallback() {
        val harness = Harness(valid(EditorUiState.Page.TITLE))

        harness.dispatcher().advance()
        assertEquals(EditorUiState.Page.SCHEDULE, harness.state.page)
        harness.dispatcher().advance()
        assertEquals(EditorUiState.Page.STEPS, harness.state.page)
        harness.dispatcher().advance()
        assertEquals(EditorUiState.Page.SUMMARY, harness.state.page)
        harness.dispatcher().save()

        assertEquals(1, harness.saveCount)
        assertEquals("Morgenroutine", harness.saved?.title)
    }

    @Test
    fun backAlwaysUsesTheCurrentAuthoritativeNavigationAndPromptState() {
        val harness = Harness(valid(EditorUiState.Page.SCHEDULE))

        harness.dispatcher().back()
        assertEquals(EditorUiState.Page.TITLE, harness.state.page)
        harness.state = harness.state.withPage(EditorUiState.Page.STEPS, false)
        harness.dispatcher().back()
        assertEquals(EditorUiState.Page.SCHEDULE, harness.state.page)
        harness.state = harness.state.withPage(EditorUiState.Page.SUMMARY, false)
        harness.dispatcher().back()
        assertEquals(EditorUiState.Page.STEPS, harness.state.page)

        harness.state = harness.state.withPage(EditorUiState.Page.TITLE, true)
        harness.dispatcher().back()
        assertEquals(EditorUiState.Page.SUMMARY, harness.state.page)
        assertFalse(harness.state.returnToSummary)

        harness.state = harness.state.withFeedback(
            harness.state.issues,
            EditorUiState.Prompt.DISCARD,
            "",
        )
        harness.dispatcher().back()
        assertEquals(EditorUiState.Prompt.NONE, harness.state.prompt)
        assertEquals(0, harness.dismissCount)
    }

    @Test
    fun closeAndDeleteRequireExplicitConfirmationAndEmitOnlyOnce() {
        val unchanged = Harness(EditorUiState.create())
        unchanged.dispatcher().requestClose()
        assertEquals(EditorUiState.Prompt.NONE, unchanged.state.prompt)
        assertEquals(1, unchanged.dismissCount)

        val create = Harness(valid(EditorUiState.Page.TITLE))
        create.dispatcher().requestClose()
        assertEquals(EditorUiState.Prompt.DISCARD, create.state.prompt)
        create.dispatcher().closePrompt()
        assertEquals(EditorUiState.Prompt.NONE, create.state.prompt)
        create.dispatcher().requestClose()
        create.dispatcher().dismiss()
        assertEquals(1, create.dismissCount)

        val editState = valid(EditorUiState.Page.SUMMARY).let {
            EditorUiState(
                true,
                false,
                false,
                "task-1",
                it.draft,
                TaskEditorNavigation(EditorUiState.Page.SUMMARY, false, null),
                TaskEditorFeedback.empty(),
                it.draft.snapshot(),
            )
        }
        val edit = Harness(editState)
        edit.dispatcher().showDeletePrompt()
        assertEquals(EditorUiState.Prompt.DELETE, edit.state.prompt)
        assertNull(edit.deletedTaskId)
        edit.dispatcher().delete()
        assertEquals("task-1", edit.deletedTaskId)
        assertEquals(1, edit.deleteCount)
    }

    private class Harness(initial: EditorUiState) {
        var state = initial
        var saved: EditorUiState? = null
        var saveCount = 0
        var deleteCount = 0
        var deletedTaskId: String? = null
        var dismissCount = 0

        fun dispatcher() = TaskEditorComposeDispatcher(
            state,
            TODAY,
            TaskEditorComposeCallbacks(
                onDraftChanged = { state = it },
                onSave = { saved = it; saveCount++ },
                onDelete = { deletedTaskId = it; deleteCount++ },
                onDismiss = { dismissCount++ },
            ),
            TaskEditorValidator(),
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 23)

        fun valid(page: EditorUiState.Page): EditorUiState = EditorUiState.create().draft(
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
            "",
            emptyList(),
            null,
            1,
        ).withPage(page, false)
    }
}
