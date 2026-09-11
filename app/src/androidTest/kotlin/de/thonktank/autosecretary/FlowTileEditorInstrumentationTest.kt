package de.thonktank.autosecretary

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses the real accessibility/input boundary on every matrix API, including API 37. */
class FlowTileEditorInstrumentationTest {
    @get:Rule val activityRule = ActivityScenarioRule(FlowTileEditorHarnessActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var ids: List<String>

    @Before fun awaitTiles() {
        assertTrue(device.wait(Until.hasObject(By.desc("Waschen")), 5_000))
        activityRule.scenario.onActivity { ids = it.editor.state.value.draft.graph.stepIds }
    }

    @Test fun stepDialogHasOnlyNameWaitAndTheDirectCheckboxAtLargeFont() {
        activityRule.scenario.onActivity { it.fontScale = 1.6f }
        button("Waschen").click()
        assertTrue(device.wait(Until.hasObject(By.text("Schritt bearbeiten")), 5_000))
        assertNotNull(device.findObject(By.desc("Name")))
        assertNotNull(device.findObject(By.desc("Wartezeit danach")))
        assertTrue(device.hasObject(By.text("Beim Start nachfragen")))
        assertFalse(device.hasObject(By.textContains("Zeitoptionen")))
        assertFalse(device.hasObject(By.textContains("Nach Ablauf bestätigen")))
        assertFalse(device.hasObject(By.text("Reihenfolge")))
        assertFalse(device.hasObject(By.text("Kapazität")))
    }

    @Test fun touchDragInsertsBeforeAndUndoRestoresTheStableGraph() {
        val origin = button("Trockner").visibleBounds
        val target = button("Aufhängen").visibleBounds
        assertTrue(device.swipe(origin.centerX(), origin.centerY(), target.centerX(), target.top + 3, 30))
        assertGraph { it.predecessors(ids[1]) == listOf(ids[2]) && it.predecessors(ids[2]) == listOf(ids[0]) }
        button("Rückgängig").click()
        assertGraph { it.links == listOf(FlowTileGraph.Link(ids[0], ids[1])) && it.stepIds == ids }
    }

    @Test fun cancelledGestureNeverWritesAPreviewIntoTheDraft() {
        val start = button("Trockner").visibleBounds
        val end = button("Aufhängen").visibleBounds
        pointer(start.centerX().toFloat(), start.centerY().toFloat(), end.centerX().toFloat(),
            end.top + 3f, cancel = true, mouse = false)
        assertGraph { it.links == listOf(FlowTileGraph.Link(ids[0], ids[1])) && it.stepIds == ids }
        activityRule.scenario.onActivity { assertFalse(it.editor.state.value.canUndo) }
    }

    @Test fun mouseCanMoveATileUsingTheSameProposalAndReleaseContract() {
        val start = button("Trockner").visibleBounds
        val end = button("Aufhängen").visibleBounds
        pointer(start.centerX().toFloat(), start.centerY().toFloat(), end.centerX().toFloat(),
            end.top + 3f, cancel = false, mouse = true)
        assertGraph { it.predecessors(ids[1]) == listOf(ids[2]) }
    }

    @Test fun explicitJoinIsAvailableWithoutDraggingAndIsNotAppliedBeforeConfirmation() {
        button("Trockner").longClick()
        button("Zusammenführen").click()
        button("Aufhängen").click()
        button("Gemeinsamer Folgeschritt").click()
        assertGraph { it.predecessors(ids[1]) == listOf(ids[0]) }
        button("Übernehmen").click()
        assertGraph { it.predecessors(ids[1]).toSet() == setOf(ids[0], ids[2]) }
        button("Rückgängig").click()
        assertGraph { it.predecessors(ids[1]) == listOf(ids[0]) }
    }

    @Test fun openStepInputSurvivesActivityRecreationWithoutAdditionalFields() {
        button("Waschen").click()
        val input = button("Name")
        input.text = "Buntwäsche"
        activityRule.scenario.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("Buntwäsche")), 5_000))
        assertTrue(device.hasObject(By.text("Beim Start nachfragen")))
        activityRule.scenario.onActivity { assertEquals("Buntwäsche", it.editor.state.value.form!!.fields["name"]) }
    }

    private fun button(label: String): UiObject2 {
        var result = device.wait(Until.findObject(By.desc(label)), 1_000)
            ?: device.findObject(By.text(label))
        if (result == null) {
            UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(5).scrollTextIntoView(label)
            result = device.wait(Until.findObject(By.desc(label)), 2_000) ?: device.findObject(By.text(label))
        }
        return requireNotNull(result) { "Missing accessible control: $label" }
    }

    private fun assertGraph(predicate: (FlowTileGraph) -> Boolean) {
        instrumentation.waitForIdleSync()
        activityRule.scenario.onActivity { assertTrue("Unexpected tile graph", predicate(it.editor.state.value.draft.graph)) }
    }

    private fun pointer(x: Float, y: Float, targetX: Float, targetY: Float, cancel: Boolean, mouse: Boolean) {
        val down = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0; toolType = if (mouse) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER
        })
        fun send(action: Int, px: Float, py: Float) {
            val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = px; this.y = py; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, properties, coordinates,
                0, if (mouse && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) MotionEvent.BUTTON_PRIMARY else 0,
                1f, 1f, 0, 0, if (mouse) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        for (i in 1..12) { SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, x + (targetX - x) * i / 12, y + (targetY - y) * i / 12) }
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, targetX, targetY)
    }
}
