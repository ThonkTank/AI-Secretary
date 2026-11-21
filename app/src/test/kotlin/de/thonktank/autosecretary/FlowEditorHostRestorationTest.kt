package de.thonktank.autosecretary

import android.os.Bundle
import android.os.Parcel
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Isolates Activity/ViewModel restoration from accessibility text matching on real devices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class FlowEditorHostRestorationTest {
    @Test fun activityRecreationRetainsTheLifecycleOwnedEditorAndOpenInput() {
        val controller = Robolectric.buildActivity(FlowTileEditorHarnessActivity::class.java).setup()
        try {
            val original = controller.get().editor
            original.openStep(original.state.value.draft.steps.first().id)
            original.field("name", "Buntwäsche")
            controller.recreate()
            assertSame(original, controller.get().editor)
            assertEquals("Buntwäsche", controller.get().editor.state.value.form!!.fields["name"])
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun largeEditorRestoresLastInputAndOneHundredUndoStepsFromASmallHostParcel() {
        val first = Robolectric.buildActivity(FlowTileEditorHarnessActivity::class.java).setup()
        var draft = FlowEditorDraft.empty().rename("Eigenständiger Ablauf")
        repeat(60) { index ->
            draft = draft.appendStep("Vorbereitung $index", FlowDelayPolicy.fixed(index * 60_000L))
        }
        val editedId = draft.steps.last().id
        first.get().render(draft)
        val original = first.get().editor
        repeat(105) { index ->
            original.openStep(editedId)
            original.field("name", "Schritt $index")
            original.submitForm()
        }
        original.openStep(editedId)
        original.field("name", "Buntwäsche")
        original.field("duration", "0,")
        val saved = Bundle()
        try { first.pause().saveInstanceState(saved).stop() } finally { first.destroy() }
        val references = editorReferences(saved)
        assertTrue("Expected a saved editor reference", references.isNotEmpty())
        for (reference in references) {
            assertEquals(setOf("version", "sessionId"), reference.keySet())
            val referenceParcel = Parcel.obtain()
            try {
                referenceParcel.writeBundle(reference)
                assertTrue("Editor reference is ${referenceParcel.dataSize()} bytes", referenceParcel.dataSize() < 4 * 1024)
            } finally { referenceParcel.recycle() }
        }
        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(saved)
            assertTrue("Unexpected host parcel size: ${parcel.dataSize()}", parcel.dataSize() < 48 * 1024)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(FlowTileEditorHarnessActivity::class.java.classLoader))
        } finally { parcel.recycle() }
        val second = Robolectric.buildActivity(FlowTileEditorHarnessActivity::class.java)
            .create(restored).start().resume().visible()
        try {
            val editor = second.get().editor
            assertNotSame(original, editor)
            assertEquals("Eigenständiger Ablauf", editor.state.value.draft.name)
            assertEquals(draft.graph.stepIds, editor.state.value.draft.graph.stepIds)
            assertEquals(draft.graph.links, editor.state.value.draft.graph.links)
            assertEquals(draft.waits, editor.state.value.draft.waits)
            val form = requireNotNull(editor.state.value.form)
            assertEquals(editedId, form.id)
            assertEquals("Buntwäsche", form.fields["name"])
            assertEquals("0,", form.fields["duration"])
            editor.closeForm()
            assertEquals("Schritt 104", editor.state.value.draft.step(editedId).text)
            for (index in 103 downTo 4) {
                assertTrue(editor.state.value.canUndo)
                editor.undo()
                assertEquals("Schritt $index", editor.state.value.draft.step(editedId).text)
            }
            assertFalse(editor.state.value.canUndo)
        } finally { second.pause().stop().destroy() }
    }

    // The harness can own both its initial editor and the selected fixture editor. Inspect their
    // actual Activity state without depending on the registry's internal container key names.
    @Suppress("DEPRECATION")
    private fun editorReferences(container: Bundle): List<Bundle> = buildList {
        for (key in container.keySet()) {
            val child = container.get(key) as? Bundle ?: continue
            if (key == "flow_tile_editor") add(child) else addAll(editorReferences(child))
        }
    }
}
