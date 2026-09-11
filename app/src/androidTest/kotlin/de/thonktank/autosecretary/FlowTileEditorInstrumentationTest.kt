package de.thonktank.autosecretary

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream
import android.util.Log

/** Uses the real accessibility/input boundary on every matrix API, including API 37. */
class FlowTileEditorInstrumentationTest {
    val activityRule = ActivityScenarioRule(FlowTileEditorHarnessActivity::class.java)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(activityRule).around(object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            // Capture before ActivityScenario closes the host, not the empty launcher afterwards.
            runCatching {
                activityRule.scenario.onActivity {
                    Log.e("FlowTileTest", "${description.methodName}: form=${it.editor.state.value.form}, graph=${it.editor.state.value.draft.graph.links}")
                }
                val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
                hierarchy.chunked(3000).forEach { Log.e("FlowTileTest", it) }
            }.onFailure { Log.e("FlowTileTest", "Could not capture failure hierarchy", it) }
        }
    })
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
        awaitFormName("Waschen")
        assertNotNull(button("Schritt bearbeiten"))
        assertNotNull(button("Name"))
        assertNotNull(button("Wartezeit danach"))
        assertNotNull(button("Beim Start nachfragen"))
        activityRule.scenario.onActivity {
            assertEquals(setOf("name", "duration", "unit", "ask"), it.editor.state.value.form!!.fields.keys)
        }
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
        accessibleClick("flow-editor:handle:JOIN")
        val targetId = "flow-editor:target:${ids[1]}"
        device.waitForIdle()
        if (!device.hasObject(By.res(targetId)))
            UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(5)
                .scrollIntoView(UiSelector().resourceId(targetId))
        accessibleClick(targetId)
        button("Gemeinsamer Folgeschritt").click()
        assertGraph { it.predecessors(ids[1]) == listOf(ids[0]) }
        button("Übernehmen").click()
        assertGraph { it.predecessors(ids[1]).toSet() == setOf(ids[0], ids[2]) }
        button("Rückgängig").click()
        assertGraph { it.predecessors(ids[1]) == listOf(ids[0]) }
    }

    @Test fun openStepInputSurvivesActivityRecreationWithoutAdditionalFields() {
        button("Waschen").click()
        val input = nameInput()
        input.text = "Buntwäsche"
        awaitFormName("Buntwäsche")
        activityRule.scenario.recreate()
        awaitFormName("Buntwäsche")
        assertEquals("Buntwäsche", nameInput().text)
        assertNotNull(button("Beim Start nachfragen"))
        activityRule.scenario.onActivity { assertEquals("Buntwäsche", it.editor.state.value.form!!.fields["name"]) }
    }

    private fun nameInput(): UiObject2 = requireNotNull(device.wait(
        Until.findObject(By.res("flow-editor:name").clazz("android.widget.EditText")), 5_000
    )) { "Missing editable name field" }

    private fun accessibleClick(resourceId: String) {
        assertTrue(device.wait(Until.hasObject(By.res(resourceId)), 5_000))
        instrumentation.waitForIdleSync()
        // Exercise the advertised non-drag accessibility action, independent of animated
        // bring-into-view coordinates. Pointer interaction has its own touch/mouse tests.
        val nodes = instrumentation.uiAutomation.rootInActiveWindow
            .findAccessibilityNodeInfosByViewId(resourceId)
        val node = requireNotNull(nodes.firstOrNull { it.isClickable }) {
            "Missing clickable accessibility node: $resourceId"
        }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun button(label: String): UiObject2 {
        // A font change or bring-into-view animation can retain the old node coordinates
        // briefly. Resolve the control after layout settles, then scroll by its actual semantics.
        device.waitForIdle()
        var result = device.wait(Until.findObject(By.desc(label)), 1_000)
            ?: device.findObject(By.text(label))
        if (result == null) {
            val scrollable = UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(5)
            if (!scrollable.scrollIntoView(UiSelector().description(label))) scrollable.scrollTextIntoView(label)
            device.waitForIdle()
            result = device.wait(Until.findObject(By.desc(label)), 2_000) ?: device.findObject(By.text(label))
        }
        return requireNotNull(result) { "Missing accessible control: $label" }
    }

    private fun awaitFormName(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var actual: String? = null
        do {
            instrumentation.waitForIdleSync()
            activityRule.scenario.onActivity { actual = it.editor.state.value.form?.fields?.get("name") }
            if (actual == expected) return
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Step input did not reach the editor state", expected, actual)
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
