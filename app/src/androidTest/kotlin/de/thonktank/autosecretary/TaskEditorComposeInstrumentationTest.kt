package de.thonktank.autosecretary

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.filters.SdkSuppress
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.presentation.editor.TrainingHistoryUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 36)
class TaskEditorComposeInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<TaskEditorComposeHarnessActivity>()

    @Before
    fun renderReferenceState() {
        compose.runOnUiThread { compose.activity.render(taskEditorComposeReferenceState()) }
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
    fun completeCreateJourneyVisitsEveryPageAndSavesOnce() {
        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.SCHEDULE }
        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.STEPS }
        compose.onNodeWithText("weiter").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.SUMMARY }
        compose.onNodeWithText("Speichern").performClick()

        compose.waitUntil { compose.activity.saveCount == 1 && compose.activity.state.saving }
        assertEquals("Morgenroutine", compose.activity.state.title)
        compose.onNodeWithText("Bitte kurz warten …").assertIsNotEnabled()
    }

    @Test
    fun everySummaryChangeRowPublishesItsDeclaredReturnTarget() {
        val summary = taskEditorComposeEditReferenceState().withPage(
            EditorUiState.Page.SUMMARY,
            false,
        )
        val targets = listOf(
            "Titel" to EditorUiState.Page.TITLE,
            "Rhythmus" to EditorUiState.Page.SCHEDULE,
            "wann am Tag" to EditorUiState.Page.SCHEDULE,
            "geschätzte Dauer" to EditorUiState.Page.SCHEDULE,
            "Zeitraum" to EditorUiState.Page.TITLE,
            "Schritte" to EditorUiState.Page.STEPS,
            "Ablauf" to EditorUiState.Page.FLOW,
            "Notiz" to EditorUiState.Page.TITLE,
        )

        targets.forEach { (label, target) ->
            compose.runOnUiThread { compose.activity.render(summary) }
            compose.waitForIdle()
            compose.onNode(hasContentDescription(label, substring = true))
                .performScrollTo()
                .performClick()
            compose.waitUntil {
                compose.activity.state.page == target && compose.activity.state.returnToSummary
            }
        }
    }

    @Test
    fun stepsCanBeAddedEditedMovedAndRemovedThroughCompose() {
        compose.runOnUiThread {
            compose.activity.render(
                taskEditorComposeReferenceState().withPage(EditorUiState.Page.STEPS, false),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("task-editor:add-step").performClick()
        compose.waitUntil { compose.activity.state.expandedStepId != null }
        val addedId = checkNotNull(compose.activity.state.expandedStepId)

        compose.onNodeWithTag("step:$addedId:title").performTextReplacement("Dritter Schritt")
        compose.waitUntil {
            compose.activity.state.stepStates.last().text == "Dritter Schritt"
        }
        compose.onNodeWithText("übernehmen").performClick()
        compose.waitUntil { compose.activity.state.expandedStepId == null }

        compose.onNodeWithContentDescription("Dehnen nach unten verschieben").performClick()
        compose.waitUntil { compose.activity.state.stepStates.first().text == "Atmen" }

        compose.onNodeWithTag("task-editor:step:$addedId").performClick()
        compose.waitUntil { compose.activity.state.expandedStepId == addedId }
        compose.onNodeWithText("entfernen").performClick()
        compose.waitUntil { compose.activity.state.stepStates.none { it.id == addedId } }
    }

    @Test
    fun optionalFlowEntryIsVisibleFromStepsAndDoesNotBecomeAWizardPage() {
        compose.runOnUiThread {
            compose.activity.render(
                taskEditorComposeReferenceState().withPage(EditorUiState.Page.STEPS, false),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("task-editor:flow-open").performScrollTo().performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.FLOW }
        compose.onNodeWithText("Was kommt wann danach?").assertExists()
        compose.onNodeWithTag("task-editor:flow-step:step-1").assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(EditorUiState.Page.FLOW, compose.activity.state.page)
        compose.onNodeWithText("fertig").performClick()
        compose.waitUntil { compose.activity.state.page == EditorUiState.Page.STEPS }
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
    fun focusedInputAndPageScrollSurviveRecreation() {
        val detail = taskEditorComposeReferenceState()
            .withPage(EditorUiState.Page.STEPS, false)
            .withExpandedStep("step-1")
        compose.runOnUiThread { compose.activity.render(detail) }
        compose.waitForIdle()
        val note = compose.onNodeWithTag("step:step-1:note")
        note.performScrollTo()
        note.performTextReplacement("nach Recreation weiter")
        compose.waitUntil { compose.activity.state.stepStates.first().note == "nach Recreation weiter" }
        val before = scrollPosition("task-editor:scroll:STEPS:step-1")
        assertTrue(before > 0f)

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("step:step-1:note").assertIsFocused()
        assertEquals("nach Recreation weiter", compose.activity.state.stepStates.first().note)
        val restored = scrollPosition("task-editor:scroll:STEPS:step-1")
        assertTrue("scroll $restored did not restore $before", restored >= before - 1f)
    }

    @Test
    fun trainingAssistantShowsLearningStateAndDedicatedStartLoadError() {
        compose.runOnUiThread { compose.activity.render(trainingAssistantEditorState()) }
        compose.waitForIdle()

        compose.onNodeWithTag("task-editor:training-status:press")
            .performScrollTo()
            .assertTextEquals("Kalibriert 0/3")
        compose.onNodeWithText("Bitte ein Startgewicht größer als 0 eingeben.")
            .assertExists()
    }

    @Test
    fun trainingHistoryIsStepLocalCollapsibleAndProtectsDirtyDrafts() {
        val clean = cleanTrainingAssistantEditorState()
        val history = mapOf(
            "press" to TrainingHistoryUiModel(
                "press",
                listOf("Wiederholungen erhöht · 3 × 11 → 3 × 12"),
                true,
            ),
        )
        compose.runOnUiThread { compose.activity.render(clean, trainingHistory = history) }
        compose.waitForIdle()

        compose.onNodeWithText("Wiederholungen erhöht · 3 × 11 → 3 × 12")
            .assertDoesNotExist()
        compose.onNodeWithTag("task-editor:training-history:press")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Wiederholungen erhöht · 3 × 11 → 3 × 12")
            .assertExists()
        compose.onNodeWithTag("task-editor:training-undo:press")
            .performScrollTo()
            .performClick()
        assertEquals(1, compose.activity.undoCount)
        assertEquals("press", compose.activity.lastUndoStepId)

        val dirty = de.thonktank.autosecretary.editor.TaskEditorStateReducer.updateTitle(
            clean,
            "Gym geändert",
        )
        compose.runOnUiThread { compose.activity.render(dirty, trainingHistory = history) }
        compose.waitForIdle()
        compose.onNodeWithTag("task-editor:training-undo:press").assertIsNotEnabled()
        compose.onNodeWithText(
            "Zum Rückgängigmachen zuerst speichern oder Änderungen verwerfen.",
        ).assertExists()

        compose.runOnUiThread { compose.activity.render(clean, trainingHistory = emptyMap()) }
        compose.waitForIdle()
        compose.onNodeWithTag("task-editor:training-history:press").assertDoesNotExist()
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

        compose.onNodeWithText("weiter bearbeiten").performClick()
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
    fun saveDeleteAndConfirmedCloseRebindBeforeTheyCanRepeat() {
        compose.runOnUiThread {
            compose.activity.render(taskEditorComposeReferenceState().withPage(
                EditorUiState.Page.SUMMARY,
                false,
            ))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil { compose.activity.saveCount == 1 && compose.activity.state.saving }
        compose.onNodeWithText("Bitte kurz warten …").assertIsNotEnabled()

        compose.runOnUiThread {
            compose.activity.render(taskEditorComposeEditReferenceState().withPage(
                EditorUiState.Page.SUMMARY,
                false,
            ))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Löschen").performClick()
        compose.onNodeWithTag("task-editor:prompt").assertExists()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitUntil { compose.activity.deleteCount == 1 && compose.activity.state.saving }
        compose.onNodeWithText("Bitte kurz warten …").assertIsNotEnabled()

        compose.runOnUiThread { compose.activity.render(taskEditorComposeReferenceState()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("abbrechen").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitUntil { compose.activity.dismissCount == 1 && !compose.activity.state.open }
        compose.onNodeWithTag("task-editor:compose").assertDoesNotExist()
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

    private fun scrollPosition(tag: String): Float = compose.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
}
