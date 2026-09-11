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

    @Test fun aNewHostRestoresAParcelledDraftAndFormForTheSelectedFixtureGeneration() {
        val first = Robolectric.buildActivity(FlowTileEditorHarnessActivity::class.java).setup()
        val draft = FlowEditorDraft.empty().rename("Eigenständiger Ablauf").addStep("Vorbereiten", FlowDelayPolicy.fixed(0))
        first.get().render(draft)
        val original = first.get().editor
        original.openStep(draft.steps.first().id)
        original.field("name", "Buntwäsche")
        val saved = Bundle()
        try { first.pause().saveInstanceState(saved).stop() } finally { first.destroy() }
        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(saved)
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
            assertEquals("Buntwäsche", editor.state.value.form!!.fields["name"])
        } finally { second.pause().stop().destroy() }
    }
}
