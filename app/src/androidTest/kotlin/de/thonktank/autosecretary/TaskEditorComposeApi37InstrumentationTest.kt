package de.thonktank.autosecretary

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
        assertState { it.page == EditorUiState.Page.SCHEDULE }
    }

    @Test
    fun restoredDiscardPromptStillNeedsAnExplicitChoice() {
        device.findObject(By.desc("abbrechen")).click()
        assertTrue(device.wait(Until.hasObject(By.text("weiter bearbeiten")), UI_TIMEOUT_MS))
        assertState { it.prompt == EditorUiState.Prompt.DISCARD }

        activityRule.scenario.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("weiter bearbeiten")), UI_TIMEOUT_MS))
        assertState { it.prompt == EditorUiState.Prompt.DISCARD }

        device.findObject(By.text("weiter bearbeiten")).click()
        assertTrue(device.wait(Until.gone(By.text("weiter bearbeiten")), UI_TIMEOUT_MS))
        assertState { it.prompt == EditorUiState.Prompt.NONE }
        activityRule.scenario.onActivity { assertEquals(0, it.dismissCount) }
    }

    @Test
    fun saveDeleteAndConfirmedCloseCrossTheAccessibilityBoundaryOnce() {
        activityRule.scenario.onActivity {
            it.render(taskEditorComposeReferenceState().withPage(EditorUiState.Page.SUMMARY, false))
        }
        assertTrue(device.wait(Until.hasObject(By.text("Speichern")), UI_TIMEOUT_MS))
        device.findObject(By.text("Speichern")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Bitte kurz warten …")), UI_TIMEOUT_MS))
        assertActivity { it.saveCount == 1 && it.state.saving }

        activityRule.scenario.onActivity {
            it.render(taskEditorComposeEditReferenceState().withPage(EditorUiState.Page.SUMMARY, false))
        }
        assertTrue(device.wait(Until.hasObject(By.text("Löschen")), UI_TIMEOUT_MS))
        device.findObject(By.text("Löschen")).click()
        assertTrue(device.wait(Until.hasObject(By.text("behalten")), UI_TIMEOUT_MS))
        device.findObject(By.text("Löschen")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Bitte kurz warten …")), UI_TIMEOUT_MS))
        assertActivity { it.deleteCount == 1 && it.state.saving }

        activityRule.scenario.onActivity { it.render(taskEditorComposeReferenceState()) }
        assertTrue(device.wait(Until.hasObject(By.desc("abbrechen")), UI_TIMEOUT_MS))
        device.findObject(By.desc("abbrechen")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Verwerfen")), UI_TIMEOUT_MS))
        activityRule.scenario.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("Verwerfen")), UI_TIMEOUT_MS))
        device.findObject(By.text("Verwerfen")).click()
        assertTrue(device.wait(Until.gone(By.text("Verwerfen")), UI_TIMEOUT_MS))
        assertActivity { it.dismissCount == 1 && !it.state.open }
    }

    private fun assertState(predicate: (EditorUiState) -> Boolean) {
        assertActivity { predicate(it.state) }
    }

    private fun assertActivity(predicate: (TaskEditorComposeHarnessActivity) -> Boolean) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertTrue("Editor state did not reach the expected value", predicate(it))
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 5_000L
    }
}
