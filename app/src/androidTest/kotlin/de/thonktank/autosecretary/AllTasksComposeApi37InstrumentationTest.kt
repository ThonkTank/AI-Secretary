package de.thonktank.autosecretary

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.presentation.alltasks.AllTasksComposeFixture
import de.thonktank.autosecretary.presentation.alltasks.AllTasksUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** API 37 canary that stays outside Compose's currently incompatible input test driver. */
@SdkSuppress(minSdkVersion = 37)
class AllTasksComposeApi37InstrumentationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(AllTasksComposeHarnessActivity::class.java)

    private val device by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun visibleComposeControlsPublishAndRestoreTheManagementState() {
        assertTrue(device.wait(Until.hasObject(By.text("Morgenroutine")), UI_TIMEOUT_MS))
        device.findObject(By.text("Sortieren")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Aufgaben")), UI_TIMEOUT_MS))
        assertActivity { it.state.mode == AllTasksUiState.Mode.SORT }

        activityRule.scenario.recreate()

        assertTrue(device.wait(Until.hasObject(By.text("Aufgaben")), UI_TIMEOUT_MS))
        assertActivity { it.state.mode == AllTasksUiState.Mode.SORT }
    }

    @Test
    fun accessibilityExpansionAndStableDropBoundaryRemainOperable() {
        assertTrue(device.wait(Until.hasObject(By.desc("Schritte anzeigen")), UI_TIMEOUT_MS))
        device.findObject(By.desc("Schritte anzeigen")).click()
        assertActivity { it.state.expandedCardKeys.isNotEmpty() }

        activityRule.scenario.onActivity { activity ->
            activity.render(
                AllTasksComposeFixture.state()
                    .toggleExpanded(AllTasksUiState.cardKey("morning", TaskSlot.MORNING))
                    .toggleExpanded(AllTasksUiState.cardKey("bed", TaskSlot.MORNING)),
            )
            assertTrue(activity.allTasks.dispatchDropForTest(
                "step:morning|MORNING:morning-step-0",
                "STEP_TARGET:bed|MORNING:end",
            ))
            assertEquals("step:morning-step-0:bed:null", activity.lastMove)
        }
    }

    private fun assertActivity(
        predicate: (AllTasksComposeHarnessActivity) -> Boolean,
    ) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertTrue("All-tasks state did not reach the expected value", predicate(it))
        }
    }

    private companion object { const val UI_TIMEOUT_MS = 5_000L }
}
