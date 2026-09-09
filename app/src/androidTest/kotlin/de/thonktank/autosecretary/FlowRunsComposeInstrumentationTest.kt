package de.thonktank.autosecretary

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.filters.SdkSuppress
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeFixture
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsSemantics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@SdkSuppress(maxSdkVersion = 36)
class FlowRunsComposeInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<FlowRunsComposeHarnessActivity>()

    @Test
    fun navigationReturnsExactlyOnceThroughTheTypedHostBoundary() {
        clickAction(FlowRunsSemantics.BACK, "back")
    }

    @Test
    fun offeredRunActionsReturnExactlyOnceThroughTheTypedHostBoundary() {
        clickAction(FlowRunsSemantics.defer("offered"), "defer:offered")
        clickAction(FlowRunsSemantics.postpone("offered"), "postpone:offered")
        clickAction(FlowRunsSemantics.moveDown("offered"), "move:offered:capacity")
        clickAction(FlowRunsSemantics.cancel("offered"), "cancel:offered")
    }

    @Test
    fun timedRunActionsReturnExactlyOnceThroughTheTypedHostBoundary() {
        clickAction(FlowRunsSemantics.readyNow("timed"), "ready:timed")
        clickAction(FlowRunsSemantics.adjustTime("timed"), "adjust:timed")
        clickAction(FlowRunsSemantics.moveUp("timed"), "move:timed:offered")
        clickAction(FlowRunsSemantics.moveDown("timed"), "move:timed:null")
        clickAction(FlowRunsSemantics.cancel("timed"), "cancel:timed")
    }

    @Test
    fun capacityRunActionsReturnExactlyOnceThroughTheTypedHostBoundary() {
        clickAction(FlowRunsSemantics.moveUp("capacity"), "move:capacity:timed")
        clickAction(FlowRunsSemantics.cancel("capacity"), "cancel:capacity")
    }

    @Test
    fun loadingEmptyAndChangingStatesStayExplicit() {
        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.loading()) }
        compose.onNodeWithTag(FlowRunsSemantics.LOADING).assertExists()

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.empty()) }
        compose.onNodeWithTag(FlowRunsSemantics.EMPTY).assertExists()

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.error()) }
        compose.onNodeWithTag(FlowRunsSemantics.ERROR).assertExists()
        compose.onNodeWithTag(FlowRunsSemantics.ERROR_DISMISS).performClick()
        compose.waitUntil { compose.activity.actionCount("dismiss-error:1") == 1 }
        assertEquals(1, compose.activity.actionCount("dismiss-error:1"))

        compose.runOnUiThread { compose.activity.render(FlowRunsComposeFixture.changing()) }
        compose.onNodeWithTag(FlowRunsSemantics.CHANGING).assertExists()
        compose.onNodeWithTag(FlowRunsSemantics.defer("offered")).assertIsNotEnabled()
    }

    private fun clickAction(tag: String, expectedAction: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(expectedAction, compose.activity.lastAction)
            assertEquals(1, compose.activity.actionCount(expectedAction))
        }
    }
}
