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
                    Log.e("FlowTileTest", "${description.methodName} ($stage): form=${it.editor.state.value.form}, graph=${it.editor.state.value.draft.graph.links.map { link -> link.source to link.target }}")
                }
                val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
                hierarchy.chunked(3000).forEach { Log.e("FlowTileTest", it) }
            }.onFailure { Log.e("FlowTileTest", "Could not capture failure hierarchy", it) }
        }
    })
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var ids: List<String>
    private var stage = "initial state"

    @Before fun awaitTiles() {
        assertTrue(device.wait(Until.hasObject(By.desc("Waschen")), 5_000))
        activityRule.scenario.onActivity { ids = it.editor.state.value.draft.graph.stepIds }
        val deadline = SystemClock.uptimeMillis() + 5_000
        var focused = false
        do {
            activityRule.scenario.onActivity { focused = it.hasWindowFocus() }
            if (focused) break
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        assertTrue("The editor must receive input before a gesture starts", focused)
        assertFalse("The editor window did not finish its transition",
            requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                .waitForStable(5_000, 300, 50, true).isTimeout)
    }

    @Test fun stepDialogHasOnlyNameWaitAndTheDirectCheckboxAtLargeFont() {
        activityRule.scenario.onActivity { it.fontScale = 1.6f }
        // Changing density posts a Compose frame after Android's main queue can
        // already be idle. Do not resolve click coordinates from the old layout.
        val deadline = SystemClock.uptimeMillis() + 5_000
        var renderedScale = 0f
        do {
            activityRule.scenario.onActivity { renderedScale = it.renderedFontScale }
            if (renderedScale == 1.6f) break
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Large font must be composed before locating the tile", 1.6f, renderedScale, 0f)
        assertFalse("Large-font layout did not settle",
            requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                .waitForStable(5_000, 300, 50, true).isTimeout)
        tapButton("Waschen")
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
        stage = "first touch insertion"
        val origin = button("Trockner").visibleBounds
        val target = button("Aufhängen").visibleBounds
        pointer(origin.centerX().toFloat(), origin.centerY().toFloat(), target.centerX().toFloat(),
            target.top + 3f, cancel = false, mouse = false)
        awaitGraph { it.predecessors(ids[1]) == listOf(ids[2]) && it.predecessors(ids[2]) == listOf(ids[0]) }
        stage = "physical undo after insertion"
        tapButton("Rückgängig")
        awaitGraph { it.links == listOf(FlowTileGraph.Link(ids[0], ids[1])) && it.stepIds == ids }
        assertFalse(requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            .waitForStable(5_000, 300, 50, true).isTimeout)
        val again = button("Trockner").visibleBounds
        val before = button("Aufhängen").visibleBounds
        stage = "second touch insertion after undo"
        pointer(again.centerX().toFloat(), again.centerY().toFloat(), before.centerX().toFloat(),
            before.top + 3f, cancel = false, mouse = false)
        awaitGraph { it.predecessors(ids[1]) == listOf(ids[2]) && it.predecessors(ids[2]) == listOf(ids[0]) }
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
        awaitGraph { it.predecessors(ids[1]) == listOf(ids[2]) }
    }

    @Test fun nativeAccessibilityCanJoinAndUndoWithoutDraggingATile() {
        stage = "native join preview"
        button("Trockner").longClick()
        clickAccessibleTag("flow-editor:handle:JOIN")
        clickAccessibleTag("flow-editor:target:${ids[1]}")
        clickAccessibleTag("flow-editor:placement:${ids[1]}:JOIN")
        awaitAccessibleTag("flow-editor:apply-move")
        assertGraph { it.predecessors(ids[1]) == listOf(ids[0]) }
        clickAccessibleTag("flow-editor:apply-move")
        stage = "native join applied"
        awaitGraph { it.predecessors(ids[1]).toSet() == setOf(ids[0], ids[2]) }
        // Scroll through the accessibility action: a generic swipe can grab a tile.
        repeat(5) { scrollAccessible(forward = false) }
        stage = "native undo after join"
        clickAccessibleLabel("Rückgängig")
        awaitGraph { it.links == listOf(FlowTileGraph.Link(ids[0], ids[1])) && it.stepIds == ids }
    }

    @Test fun holdingATileAtTheViewportEdgeScrollsAndCancelKeepsTheGraph() {
        var draft = FlowEditorDraft.empty().rename("Langer Ablauf")
        repeat(18) { draft = draft.addStep("Schritt ${it + 1}", de.thonktank.autosecretary.domain.model.FlowDelayPolicy.fixed(0)) }
        val ordered = draft.steps.map { it.id }
        val graph = FlowTileGraph(ordered, ordered.zipWithNext { a, b -> FlowTileGraph.Link(a, b) })
        val prepared = draft.withGraph(graph)
        activityRule.scenario.onActivity { it.render(prepared) }
        val first = button("Schritt 1").visibleBounds
        val viewport = requireNotNull(device.findObject(By.res("flow-editor"))).visibleBounds
        pointer(first.centerX().toFloat(), first.centerY().toFloat(), first.centerX().toFloat(),
            viewport.bottom - 8f, cancel = true, mouse = false, whileHeld = {
                SystemClock.sleep(1_000)
                // Re-query while the viewport is moving; retaining a UiObject2 across
                // frames can throw StaleObjectException exactly when it scrolls away.
                assertTrue("The edge hold must scroll the title out of view",
                    device.wait(Until.gone(By.res("flow-editor:title")), 2_000))
                assertGraph { it.links == graph.links }
            })
        assertGraph { it.links == graph.links && it.stepIds == graph.stepIds }
        activityRule.scenario.onActivity { assertFalse(it.editor.state.value.canUndo) }
    }

    @Test fun openStepInputSurvivesActivityRecreationWithoutAdditionalFields() {
        tapButton("Waschen")
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

    private fun accessibilityNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun walk(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // Compose replaces virtual descendants during layout. Do not traverse
            // cached child lists from the preceding frame/arrangement page.
            if (!node.refresh()) return null
            if (predicate(node)) return node
            repeat(node.childCount) { index -> node.getChild(index)?.let { walk(it)?.let { found -> return found } } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
    }

    private fun scrollAccessible(forward: Boolean): Boolean {
        val node = accessibilityNode { it.isScrollable } ?: return false
        val changed = node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        device.waitForIdle()
        return changed
    }

    private fun clickAccessibleTag(tag: String) {
        val node = awaitAccessibleTag(tag)
        assertTrue("Missing click action for $tag", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun clickAccessibleLabel(label: String) {
        // Keep the non-drag journey semantic after scrolling as well instead of
        // switching to UiObject2's short coordinate gesture for the final action.
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var node = accessibilityNode {
                it.isVisibleToUser && (it.contentDescription?.toString() == label || it.text?.toString() == label)
            }
            while (node != null && !node.isClickable) node = node.parent
            if (node != null) {
                assertTrue("Missing click action for $label", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return
            }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Missing accessible button: $label")
    }

    private fun awaitAccessibleTag(tag: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            device.waitForIdle()
            val node = accessibilityNode { it.viewIdResourceName == tag && it.isVisibleToUser }
            if (node != null) return node
            // Six immediate tree reads used to finish before the posted Compose
            // frame could add the requested target. Wait for the actual control.
            SystemClock.sleep(50)
            scrollAccessible(forward = true)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Missing accessible control: $tag")
    }

    private fun awaitGraph(predicate: (FlowTileGraph) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var matched = false
            activityRule.scenario.onActivity { matched = predicate(it.editor.state.value.draft.graph) }
            if (matched) return
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        assertGraph(predicate)
    }

    private fun button(label: String): UiObject2 {
        // A font change or bring-into-view animation can retain the old node coordinates
        // briefly. Resolve the control after layout settles, then scroll by its actual semantics.
        device.waitForIdle()
        assertFalse("Layout must settle before locating $label",
            requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                .waitForStable(5_000, 300, 50, true).isTimeout)
        var result = device.wait(Until.findObject(By.desc(label)), 1_000)
            ?: device.findObject(By.text(label))
        if (result == null) {
            val scrollable = UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(5)
            if (!scrollable.scrollIntoView(UiSelector().description(label))) scrollable.scrollTextIntoView(label)
            device.waitForIdle()
            assertFalse("Scrolling must settle before locating $label",
                requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                    .waitForStable(5_000, 300, 50, true).isTimeout)
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
        activityRule.scenario.onActivity {
            val graph = it.editor.state.value.draft.graph
            assertTrue("Unexpected tile graph at $stage: ${graph.links.map { link -> link.source to link.target }}", predicate(graph))
        }
    }

    private fun tapButton(label: String) {
        val bounds = button(label).visibleBounds
        // The same synchronous event boundary as the physical drags, with no
        // movement/slop: a real held fingertip, not UiObject2's 16-ms gesture.
        pointer(bounds.centerX().toFloat(), bounds.centerY().toFloat(),
            bounds.centerX().toFloat(), bounds.centerY().toFloat(), cancel = false, mouse = false)
    }

    private fun pointer(x: Float, y: Float, targetX: Float, targetY: Float, cancel: Boolean, mouse: Boolean,
                        whileHeld: (() -> Unit)? = null) {
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
        try {
            if (x == targetX && y == targetY) {
                // Do not send stationary drag frames: synchronous dispatch under
                // load can stretch those beyond the long-press threshold.
                SystemClock.sleep(50)
            } else {
                for (i in 1..12) { SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE, x + (targetX - x) * i / 12, y + (targetY - y) * i / 12) }
            }
            whileHeld?.invoke()
        } finally {
            send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, targetX, targetY)
        }
    }
}
