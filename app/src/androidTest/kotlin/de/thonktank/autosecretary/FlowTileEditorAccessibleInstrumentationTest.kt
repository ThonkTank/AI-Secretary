package de.thonktank.autosecretary

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Semantic scrolling must not accidentally grab a tile; pointer tests use a separate real-time host. */
class FlowTileEditorAccessibleInstrumentationTest {
    @get:Rule val compose = createAndroidComposeRule<FlowTileEditorHarnessActivity>()

    @Test fun explicitJoinIsAvailableWithoutDraggingAndIsNotAppliedBeforeConfirmation() {
        val ids = compose.activity.editor.state.value.draft.graph.stepIds
        compose.onNodeWithTag("flow-editor:tile:${ids[2]}")
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithTag("flow-editor:handle:JOIN").performScrollTo().performClick()
        compose.onNodeWithTag("flow-editor:target:${ids[1]}").performScrollTo().performClick()
        compose.onNodeWithTag("flow-editor:placement:${ids[1]}:JOIN").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ids[0]), compose.activity.editor.state.value.draft.graph.predecessors(ids[1])) }
        compose.onNodeWithTag("flow-editor:apply-move").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf(ids[0], ids[2]), compose.activity.editor.state.value.draft.graph.predecessors(ids[1]).toSet()) }
        compose.onNodeWithContentDescription("Rückgängig").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ids[0]), compose.activity.editor.state.value.draft.graph.predecessors(ids[1])) }
    }
}
