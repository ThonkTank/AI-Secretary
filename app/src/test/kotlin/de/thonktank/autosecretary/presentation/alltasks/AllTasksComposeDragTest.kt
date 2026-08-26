package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class AllTasksComposeDragTest {
    @Test
    fun dragSourceRequiresAnEnabledRowAtThePointerPosition() {
        val bounds = linkedMapOf(
            "archived" to Rect(0f, 0f, 100f, 40f),
            "enabled" to Rect(0f, 50f, 100f, 90f),
        )

        assertEquals("enabled", dragSourceAt(75f, setOf("enabled"), bounds))
        assertEquals(null, dragSourceAt(20f, setOf("enabled"), bounds))
        assertEquals(null, dragSourceAt(Float.NaN, setOf("enabled"), bounds))
    }

    @Test
    fun visibleBoundsSelectTheContainingRowThenTheNearestStableKey() {
        val bounds = linkedMapOf(
            "source" to Rect(0f, 0f, 100f, 40f),
            "before" to Rect(0f, 50f, 100f, 90f),
            "after" to Rect(0f, 100f, 100f, 140f),
        )

        assertEquals("before", nearestDropTarget(75f, "source", bounds))
        assertEquals("after", nearestDropTarget(96f, "source", bounds))
        assertEquals(null, nearestDropTarget(Float.NaN, "source", bounds))
    }

    @Test
    fun edgeVelocityIsFrameIndependentAndStopsInTheCenter() {
        val viewport = Rect(0f, 100f, 100f, 500f)

        assertEquals(-460f, edgeScrollVelocity(100f, viewport, 64f, 460f), .001f)
        assertEquals(0f, edgeScrollVelocity(300f, viewport, 64f, 460f), .001f)
        assertEquals(460f, edgeScrollVelocity(500f, viewport, 64f, 460f), .001f)
    }
}
