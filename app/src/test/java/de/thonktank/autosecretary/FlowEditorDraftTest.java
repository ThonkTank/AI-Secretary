package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import android.os.Parcel;
import android.os.Bundle;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowTileGraph.Placement;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FlowEditorDraftTest {
    @Test public void namesAndWaitsMoveWithStableTileIds() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche")
                .addStep("Waschen", FlowDelayPolicy.rememberLast(7_200_000))
                .addStep("Aufhängen", FlowDelayPolicy.fixed(86_400_000));
        String wash = draft.steps.get(0).id, hang = draft.steps.get(1).id;
        FlowEditorDraft arranged = draft.withGraph(draft.graph.place(hang, wash, Placement.AFTER, false));
        assertEquals(Collections.singletonList(wash), arranged.graph.predecessors(hang));
        assertEquals(7_200_000, arranged.waits.get(wash).defaultDelayMillis);
        assertEquals(86_400_000, arranged.waits.get(hang).defaultDelayMillis);
        assertEquals(draft.waits, arranged.waits);
        FlowEditorDraft renamed = arranged.editStep(wash, "Buntwäsche", arranged.waits.get(wash));
        assertEquals(wash, renamed.steps.get(0).id);
        assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, renamed.waits.get(wash).mode);
        assertEquals("Waschen", arranged.steps.get(0).text);
    }

    @Test public void entireDraftSurvivesParcelWithResourcesCadenceAndReleasePoint() {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche")
                .addStep("Waschen", FlowDelayPolicy.rememberLast(7_200_000))
                .addStep("Abhängen", FlowDelayPolicy.fixed(0));
        String first = draft.steps.get(0).id, last = draft.steps.get(1).id;
        draft = draft.withGraph(draft.graph.place(last, first, Placement.AFTER, false)).cadence(first, 5);
        FlowEditorCapacities capacities = draft.capacities.addResource("Ständer", 3);
        capacities = capacities.addLease(capacities.resources.get(0).key, first, last, 1);
        draft = draft.withCapacities(capacities).releaseAfter(capacities.leases.get(0).key, true);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(draft.toBundle()); parcel.setDataPosition(0);
            Bundle saved = parcel.readBundle(getClass().getClassLoader());
            FlowEditorDraft restored = FlowEditorDraft.fromBundle(saved);
            assertEquals(draft.name, restored.name);
            assertEquals(draft.graph.links, restored.graph.links);
            assertEquals(draft.waits, restored.waits);
            assertEquals(draft.releaseAfterWait, restored.releaseAfterWait);
            assertEquals(Integer.valueOf(5), restored.step(first).intervalDays);
            assertEquals(3, restored.capacities.resources.get(0).capacity);
            assertEquals(last, restored.capacities.leases.get(0).releaseStepId);
            assertNotEquals(first, restored.addStep("Neu", FlowDelayPolicy.fixed(0)).steps.get(2).id);
        } finally { parcel.recycle(); }
    }

    @Test public void cancelledProposalsLeaveFullDraftUnchanged() {
        FlowEditorDraft draft = FlowEditorDraft.empty().addStep("Waschen", FlowDelayPolicy.fixed(0));
        FlowEditorDraft proposal = draft.withCapacities(draft.capacities.addResource("Maschine", 1));
        assertTrue(draft.capacities.resources.isEmpty());
        assertEquals(1, proposal.capacities.resources.size());
        assertThrows(IllegalArgumentException.class, () -> draft.editStep("missing", "Text", FlowDelayPolicy.fixed(0)));
    }
}
