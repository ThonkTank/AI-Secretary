package de.thonktank.autosecretary

import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.domain.model.FlowTileGraph
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowEditorViewModelTest {
    @Test fun stepFormContainsOnlyNameDurationUnitAndQuestion() {
        val vm = FlowEditorViewModel(FlowEditorDraft.empty(), SavedStateHandle())
        vm.openStep(null)
        assertEquals(setOf("name", "duration", "unit", "ask"), vm.state.value.form!!.fields.keys)
        vm.field("name", "Waschen"); vm.field("duration", "2"); vm.field("unit", "3600000")
        vm.field("ask", "true"); vm.submitForm()
        val draft = vm.state.value.draft
        assertNull(vm.state.value.form)
        assertEquals(7_200_000, draft.waits.getValue(draft.steps.single().id).defaultDelayMillis)
        assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, draft.waits.getValue(draft.steps.single().id).mode)
        vm.undo(); assertTrue(vm.state.value.draft.steps.isEmpty())
    }

    @Test fun formHistoryAndCapacityDraftSurviveActualParcelRoundTrip() {
        val saved = SavedStateHandle()
        val vm = FlowEditorViewModel(FlowEditorDraft.empty().rename("Wäsche"), saved)
        vm.openStep(null); vm.field("name", "Waschen"); vm.submitForm()
        vm.next(); vm.openResource(null); vm.field("name", "Maschine"); vm.submitForm()
        vm.openStep(vm.state.value.draft.steps.single().id); vm.field("name", "Buntwäsche")
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(saved.get<Bundle>("flow_tile_editor")); parcel.setDataPosition(0)
            val copy = parcel.readBundle(javaClass.classLoader)
            val restored = FlowEditorViewModel(FlowEditorDraft.empty(), SavedStateHandle(mapOf("flow_tile_editor" to copy)))
            assertEquals(2, restored.state.value.page)
            assertEquals("Buntwäsche", restored.state.value.form!!.fields["name"])
            assertEquals("Maschine", restored.state.value.draft.capacities.resources.single().name)
            restored.closeForm(); assertEquals("Waschen", restored.state.value.draft.steps.single().text)
            restored.undo(); assertTrue(restored.state.value.draft.capacities.resources.isEmpty())
            assertEquals("Wäsche", restored.state.value.draft.name)
        } finally { parcel.recycle() }
    }

    @Test fun invalidTimeDoesNotCommitAndSubMinuteTimeCanBeEdited() {
        val initial = FlowEditorDraft.empty().addStep("Test", FlowDelayPolicy.fixed(1234L))
        val vm = FlowEditorViewModel(initial, SavedStateHandle())
        vm.openStep(initial.steps.single().id); vm.submitForm()
        assertEquals(1234L, vm.state.value.draft.waits.values.single().defaultDelayMillis)
        vm.openStep(initial.steps.single().id); vm.field("duration", "-1"); vm.submitForm()
        assertNotNull(vm.state.value.error); assertNotNull(vm.state.value.form)
        assertEquals(1234L, vm.state.value.draft.waits.values.single().defaultDelayMillis)
    }

    @Test fun capacitiesCanBeAcquiredAndReleasedAtTheSameStepAfterItsWait() {
        val vm = FlowEditorViewModel(FlowEditorDraft.empty().rename("Waschen")
            .addStep("Waschen", FlowDelayPolicy.fixed(7_200_000L)), SavedStateHandle())
        vm.next(); vm.openResource(null); vm.field("name", "Maschine"); vm.submitForm()
        vm.openLease(); vm.field("afterWait", "true"); vm.submitForm()
        assertNull(vm.state.value.error)
        val lease = vm.state.value.draft.capacities.leases.single()
        assertEquals(lease.acquireStepId, lease.releaseStepId)
        assertTrue(vm.state.value.draft.releaseAfterWait.getValue(lease.key))
    }

    @Test fun accessibleMoveAndUndoUseTheSameTopologyOperation() {
        val initial = FlowEditorDraft.empty().addStep("A", FlowDelayPolicy.fixed(0))
            .addStep("B", FlowDelayPolicy.fixed(0))
        val vm = FlowEditorViewModel(initial, SavedStateHandle())
        vm.place(initial.steps[1].id, initial.steps[0].id, FlowTileGraph.Placement.AFTER)
        assertEquals(1, vm.state.value.draft.graph.links.size)
        vm.undo(); assertTrue(vm.state.value.draft.graph.links.isEmpty())
    }
}
