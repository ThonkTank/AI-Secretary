package de.thonktank.autosecretary

import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** API 37 canary that stays outside Compose's currently incompatible input test driver. */
@SdkSuppress(minSdkVersion = 37)
class TaskEditorComposeApi37InstrumentationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(TaskEditorComposeHarnessActivity::class.java)

    private val device by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Before
    fun renderReferenceState() {
        activityRule.scenario.onActivity { it.render(taskEditorComposeReferenceState()) }
        assertTrue(device.wait(Until.hasObject(By.text("Morgenroutine")), UI_TIMEOUT_MS))
    }

    @Test
    fun accessibilitySurfaceNavigatesTheRenderedComposeEditor() {
        device.findObject(By.text("weiter")).click()

        assertTrue(device.wait(Until.hasObject(By.text("Wann kommt das dran?")), UI_TIMEOUT_MS))
        waitForState { it.page == EditorUiState.Page.SCHEDULE }
    }

    @Test
    fun restoredDiscardPromptStillNeedsAnExplicitChoice() {
        device.findObject(By.desc("abbrechen")).click()
        assertTrue(device.wait(Until.hasObject(By.text("weiter bearbeiten")), UI_TIMEOUT_MS))
        waitForState { it.prompt == EditorUiState.Prompt.DISCARD }

        activityRule.scenario.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("weiter bearbeiten")), UI_TIMEOUT_MS))
        waitForState { it.prompt == EditorUiState.Prompt.DISCARD }

        device.findObject(By.text("weiter bearbeiten")).click()
        waitForState { it.prompt == EditorUiState.Prompt.NONE }
        activityRule.scenario.onActivity { assertEquals(0, it.dismissCount) }
    }

    private fun waitForState(predicate: (EditorUiState) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        do {
            var matched = false
            activityRule.scenario.onActivity { matched = predicate(it.state) }
            if (matched) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Editor state did not reach the expected value")
    }

    private companion object {
        const val UI_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
    }
}
