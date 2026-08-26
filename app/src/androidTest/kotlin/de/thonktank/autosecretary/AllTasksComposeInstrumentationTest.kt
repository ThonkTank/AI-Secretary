package de.thonktank.autosecretary

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.filters.SdkSuppress
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 36)
class AllTasksComposeInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<AllTasksComposeHarnessActivity>()

    @Test
    fun searchFilterAndModeActionsReturnThroughTheAuthoritativeState() {
        compose.onNodeWithTag("all-tasks:search").performTextReplacement("Statistik")
        compose.waitUntil { compose.activity.state.query == "Statistik" }
        compose.onNodeWithText("Abgabe Statistik-Übung").assertExists()
        compose.onNodeWithText("Morgenroutine").assertDoesNotExist()

        compose.onNodeWithTag("all-tasks:search").performTextReplacement("")
        compose.onNodeWithTag("all-tasks:filter:slots").performClick()
        compose.onNodeWithTag("all-tasks:dropdown:slots").assertExists()
        compose.onNodeWithText("Abend").performClick()
        compose.waitUntil { compose.activity.state.slots == setOf(TaskSlot.EVENING) }
        compose.onNodeWithText("Bett machen").assertDoesNotExist()
        compose.onNodeWithTag("all-tasks:overlay").performClick()

        compose.onNodeWithText("Sortieren").performClick()
        compose.waitUntil { compose.activity.state.mode == AllTasksUiState.Mode.SORT }
        compose.onNodeWithText("Aufgaben").assertExists()
    }

    @Test
    fun expansionAndFiltersSurviveActivityRecreation() {
        compose.onAllNodesWithContentDescription("Schritte anzeigen")[0].performClick()
        compose.waitUntil { compose.activity.state.expandedCardKeys.isNotEmpty() }
        val expanded = compose.activity.state.expandedCardKeys.single()
        compose.onNodeWithTag("all-tasks:search").performTextReplacement("Routine")
        compose.waitUntil { compose.activity.state.query == "Routine" }

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        assertEquals("Routine", compose.activity.state.query)
        assertTrue(compose.activity.state.expandedCardKeys.contains(expanded))
        compose.onNodeWithText("Morgenroutine").assertExists()
    }

    @Test
    fun lazyListVirtualizesADeepCatalogAndKeepsStableRowsAddressable() {
        compose.runOnUiThread { compose.activity.render(AllTasksComposeFixture.longState(200)) }
        compose.waitForIdle()
        compose.onNodeWithText("Aufgabe 0").assertExists()
        compose.onNodeWithText("Aufgabe 199").assertDoesNotExist()

        compose.onNodeWithTag("all-tasks:list").performScrollToIndex(199)

        compose.onNodeWithText("Aufgabe 199").assertExists()
        compose.onNodeWithText("Aufgabe 0").assertDoesNotExist()
    }

    @Test
    fun dragTargetsAndDropMappingUseTheProjectedStableKeys() {
        val expanded = AllTasksComposeFixture.state()
            .toggleExpanded(AllTasksUiState.cardKey("morning", TaskSlot.MORNING))
            .toggleExpanded(AllTasksUiState.cardKey("bed", TaskSlot.MORNING))
        compose.runOnUiThread {
            compose.activity.render(expanded)
            compose.activity.allTasks.setDragSourceForTest(
                "step:morning|MORNING:morning-step-0",
            )
        }
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithContentDescription(
                "Schritt an dieser Position ablegen.",
            ).fetchSemanticsNodes().isNotEmpty(),
        )

        var handled = false
        compose.runOnUiThread {
            handled = compose.activity.allTasks.dispatchDropForTest(
                "step:morning|MORNING:morning-step-0",
                "STEP_TARGET:bed|MORNING:end",
            )
        }
        assertTrue(handled)
        assertEquals("step:morning-step-0:bed:null", compose.activity.lastMove)
    }

    @Test
    fun accessibilityExposesOnlyValidOrganizationActions() {
        val expanded = AllTasksComposeFixture.state().toggleExpanded(
            AllTasksUiState.cardKey("morning", TaskSlot.MORNING),
        )
        compose.runOnUiThread { compose.activity.render(expanded) }
        compose.waitForIdle()
        val row = compose.onNodeWithTag(
            "all-tasks:row:step:morning|MORNING:morning-step-0",
        ).fetchSemanticsNode()
        val actions = row.config[SemanticsActions.CustomActions]
        val labels = actions.map { it.label }
        assertFalse(labels.contains("Schritt nach oben verschieben"))
        assertTrue(labels.contains("Schritt nach unten verschieben"))
        assertTrue(labels.contains("Schritt zur nächsten Aufgabe verschieben"))

        val down = actions.first { it.label == "Schritt nach unten verschieben" }
        compose.runOnUiThread { assertTrue(down.action()) }
        assertEquals(
            "step:morning-step-0:morning:morning-step-2",
            compose.activity.lastMove,
        )

        val select = actions.first { it.label == "Schritt zum Tauschen auswählen" }
        compose.runOnUiThread { assertTrue(select.action()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            "Schritt zum Tauschen ausgewählt.",
            substring = true,
        ).assertExists()
        compose.onNodeWithTag("all-tasks:filter:slots").performClick()
        compose.onNodeWithTag("all-tasks:dropdown:slots").assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("all-tasks:dropdown:slots").assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Schritt zum Tauschen ausgewählt.",
            substring = true,
        ).assertDoesNotExist()
        val restoredActions = compose.onNodeWithTag(
            "all-tasks:row:step:morning|MORNING:morning-step-1",
        ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertFalse(restoredActions.any { it.label == "Mit ausgewähltem Schritt tauschen" })
    }
}
