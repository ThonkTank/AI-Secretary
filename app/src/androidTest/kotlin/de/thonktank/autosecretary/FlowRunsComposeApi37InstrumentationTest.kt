package de.thonktank.autosecretary

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 37)
class FlowRunsComposeApi37InstrumentationTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(FlowRunsComposeHarnessActivity::class.java)

    private val device by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun visibleRunActionCrossesTheHostBoundary() {
        assertTrue(device.wait(Until.hasObject(By.text("Sauerteigbrot")), UI_TIMEOUT_MS))
        val ready = device.wait(Until.findObject(By.desc("Jetzt bereit")), UI_TIMEOUT_MS)
        assertNotNull(ready)
        ready.click()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity {
            assertTrue(it.lastAction == "ready:timed")
        }
    }

    private companion object { const val UI_TIMEOUT_MS = 5_000L }
}
