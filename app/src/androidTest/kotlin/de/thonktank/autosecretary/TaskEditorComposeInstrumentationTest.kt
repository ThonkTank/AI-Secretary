package de.thonktank.autosecretary

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskEditorComposeInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<TaskEditorComposeHarnessActivity>()

    @Before
    fun renderReferenceState() {
        compose.runOnUiThread { compose.activity.render(referenceState()) }
        compose.waitForIdle()
    }

    @Test
    fun titleAndWizardNavigationPublishThroughTheSingleEditorState() {
        compose.onNodeWithTag("task:title").performTextReplacement("Abendroutine")
        compose.waitUntil { compose.activity.state.title == "Abendroutine" }
        assertEquals(EditorUiState.Page.TITLE, compose.activity.state.page)

        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.SCHEDULE }
        compose.onNodeWithText("Wann kommt das dran?").assertExists()
        compose.onNodeWithText("täglich").performClick()
        assertEquals(Recurrence.DAILY, compose.activity.state.recurrence)
    }

    @Test
    fun recreationKeepsTheAuthoritativeDraftAndCurrentPage() {
        compose.onNodeWithTag("task:title").performTextReplacement("Nach Recreation")
        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.SCHEDULE }

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText("Wann kommt das dran?").assertExists()
        assertEquals("Nach Recreation", compose.activity.state.title)
        assertEquals(EditorUiState.Page.SCHEDULE, compose.activity.state.page)
    }

    @Test
    fun discardPromptSurvivesRecreationAndNeedsAnExplicitChoice() {
        compose.onNodeWithContentDescription("abbrechen").performClick()
        compose.onNodeWithTag("task-editor:prompt").assertExists()
        assertEquals(EditorUiState.Prompt.DISCARD, compose.activity.state.prompt)
        compose.onNodeWithContentDescription("abbrechen").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("task-editor:prompt").assertExists()
        assertEquals(EditorUiState.Prompt.DISCARD, compose.activity.state.prompt)

        compose.onNodeWithText("behalten").performClick()
        compose.waitUntil { compose.activity.state.prompt == EditorUiState.Prompt.NONE }
        assertEquals(0, compose.activity.dismissCount)
    }

    @Test
    fun hostBackDispatcherUsesTheSameStateDrivenPromptPath() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil { compose.activity.state.prompt == EditorUiState.Prompt.DISCARD }
        compose.onNodeWithTag("task-editor:prompt").assertExists()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil { compose.activity.state.prompt == EditorUiState.Prompt.NONE }
        compose.onNodeWithTag("task-editor:prompt").assertDoesNotExist()
        assertEquals(0, compose.activity.dismissCount)
    }

    @Test
    fun controlsExposeMinimumTargetsAndStepMoveActions() {
        val cancelBounds = compose.onNodeWithContentDescription("abbrechen")
            .fetchSemanticsNode().boundsInRoot
        val minimum = with(compose.density) { 48f * density }
        assertTrue(cancelBounds.width >= minimum)
        assertTrue(cancelBounds.height >= minimum)

        compose.onNodeWithText("weiter").performClick()
        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.STEPS }
        compose.onNodeWithTag("task-editor:step:step-1").assertExists()
        compose.onNodeWithContentDescription("Dehnen nach unten verschieben").assertExists()
    }

    private fun referenceState(): EditorUiState {
        val steps = listOf(
            EditorStepState("step-1", "Dehnen", 0, StepAmount.none(), ""),
            EditorStepState("step-2", "Atmen", 0, StepAmount.none(), ""),
        )
        return EditorUiState.create().draft(
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
            steps,
            null,
            3,
        )
    }
}
