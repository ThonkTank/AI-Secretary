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
    fun everyVisibleActionReturnsExactlyOnceThroughTheTypedHostBoundary() {
        click(FlowRunsSemantics.BACK, "back")
        click(FlowRunsSemantics.defer("offered"), "defer:offered")
        click(FlowRunsSemantics.postpone("offered"), "postpone:offered")
        click(FlowRunsSemantics.moveDown("offered"), "move:offered:capacity")
        click(FlowRunsSemantics.cancel("offered"), "cancel:offered")
        click(FlowRunsSemantics.readyNow("timed"), "ready:timed")
        click(FlowRunsSemantics.adjustTime("timed"), "adjust:timed")
        click(FlowRunsSemantics.moveUp("timed"), "move:timed:offered")
        click(FlowRunsSemantics.moveDown("timed"), "move:timed:null")
        click(FlowRunsSemantics.cancel("timed"), "cancel:timed")
        click(FlowRunsSemantics.moveUp("capacity"), "move:capacity:timed")
        click(FlowRunsSemantics.cancel("capacity"), "cancel:capacity")
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

    @Test
    private fun click(tag: String, expectedAction: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil { compose.activity.actionCount(expectedAction) == 1 }
        assertEquals(1, compose.activity.actionCount(expectedAction))
    }
}
