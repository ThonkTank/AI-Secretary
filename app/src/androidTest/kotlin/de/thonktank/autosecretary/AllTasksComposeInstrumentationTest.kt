package de.thonktank.autosecretary

import android.view.ViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
        compose.onNodeWithTag("all-tasks:overlay")
            .performSemanticsAction(SemanticsActions.OnClick)

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
        assertEquals(
            2,
            compose.onAllNodesWithText("Morgenroutine").fetchSemanticsNodes().size,
        )
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
    fun longPressPointerDragUsesTheSameStableDropBoundary() {
        val expanded = AllTasksComposeFixture.state()
            .toggleExpanded(AllTasksUiState.cardKey("morning", TaskSlot.MORNING))
            .toggleExpanded(AllTasksUiState.cardKey("bed", TaskSlot.MORNING))
        compose.runOnUiThread { compose.activity.render(expanded) }
        compose.waitForIdle()

        val source = compose.onNodeWithTag(
            "all-tasks:row:step:morning|MORNING:morning-step-0",
        )

        PresentationTrace.clear()
        source.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(longPressDurationMillis())
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithContentDescription(
                "Schritt an dieser Position ablegen.",
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetCenter = compose.onNodeWithTag(
            "all-tasks:row:step:morning|MORNING:morning-step-1",
        ).fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput {
            moveTo(Offset(
                targetCenter.x - sourceBounds.left,
                targetCenter.y - sourceBounds.top,
            ), 300)
            up()
        }

        compose.waitUntil(5_000) { compose.activity.lastMove != null }
        val trace = PresentationTrace.describe()
        assertTrue(trace, trace.contains("all-tasks-drag/start"))
        assertTrue(trace, trace.contains("all-tasks-drag/drop"))
        assertEquals("swap:morning-step-0:morning-step-1", compose.activity.lastMove)
    }

    @Test
    fun activeLongPressDragScrollsAtTheLazyListEdgeAndStopsAfterRelease() {
        compose.runOnUiThread { compose.activity.render(AllTasksComposeFixture.longDragState(60)) }
        compose.waitForIdle()
        val source = compose.onNodeWithTag(
            "all-tasks:row:step:drag-source|MORNING:drag-source-step-0",
        )
        PresentationTrace.clear()
        source.performTouchInput { down(center) }
        try {
            compose.mainClock.advanceTimeBy(longPressDurationMillis())
            compose.waitForIdle()
            val sourceBounds = source.fetchSemanticsNode().boundsInRoot
            val listBounds = compose.onNodeWithTag("all-tasks:list")
                .fetchSemanticsNode().boundsInRoot
            val edge = Offset(
                sourceBounds.center.x - sourceBounds.left,
                listBounds.bottom - sourceBounds.top - 2,
            )
            compose.mainClock.autoAdvance = false
            source.performTouchInput { moveTo(edge, 100) }
            repeat(120) { compose.mainClock.advanceTimeByFrame() }
        } finally {
            source.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()

        val settled = compose.onNodeWithTag("all-tasks:list").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val trace = PresentationTrace.describe()
        assertTrue(trace, trace.contains("all-tasks-drag/start"))
        assertTrue(trace, trace.contains("all-tasks-drag/edge direction=1"))
        assertTrue(trace, settled > 0f)
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

    private fun longPressDurationMillis(): Long =
        ViewConfiguration.getLongPressTimeout().toLong() + 150L
}
