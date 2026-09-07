package de.thonktank.autosecretary

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 36)
class FlowRunsComposeInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<FlowRunsComposeHarnessActivity>()

    @Test
    fun contextualActionsReturnThroughTheTypedHostBoundary() {
        compose.onNodeWithContentDescription("Später").performClick()
        compose.waitUntil { compose.activity.lastAction == "defer:offered" }

        compose.onNodeWithContentDescription("Jetzt bereit").performClick()
        compose.waitUntil { compose.activity.lastAction == "ready:timed" }

        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil { compose.activity.lastAction == "back" }
    }

    @Test
    fun loadingEmptyAndChangingStatesStayExplicit() {
        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.loading()) }
        compose.onNodeWithTag("flow-runs:loading").assertExists()

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.empty()) }
        compose.onNodeWithTag("flow-runs:empty").assertExists()

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.error()) }
        compose.onNodeWithTag("flow-runs:error").assertExists()

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.changing()) }
        compose.onNodeWithTag("flow-runs:changing").assertExists()
        compose.onNodeWithContentDescription("Später").assertIsNotEnabled()
    }

    @Test
    fun reorderCallbacksKeepThePublishedRunIdentity() {
        compose.onAllNodesWithContentDescription("Weiter nach hinten")[0].performClick()
        compose.waitUntil { compose.activity.lastAction != null }
        assertEquals("move:offered:capacity", compose.activity.lastAction)
    }
}
