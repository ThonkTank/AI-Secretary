package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FlowOccurrenceIdentityTest {
    private final StepSnapshotFactory factory = new StepSnapshotFactory(() -> "occurrence-step");

    @Test public void frozenGraphIdentityAndPayloadReachTheOccurrenceWithoutConsultingTheDefinition() {
        OccurrenceStep step = factory.fromFlow(runtime(StepAmount.none(), FlowGraphRun.State.AVAILABLE), "occurrence", 0);
        assertEquals("runtime-step", step.flowRunStepId);
        assertEquals("source-template", step.sourceTemplateId);
        assertEquals("Frozen name", step.text);
        assertEquals("Frozen note", step.note);
        assertEquals("step:source-template", step.comboOwnerId);
        assertFalse(step.done);
        assertEquals("runtime-step", step.complete().flowRunStepId);
        assertEquals("runtime-step", step.complete().reopen().flowRunStepId);
    }

    @Test public void quantitativeEditsCarryAndRelocationPreserveTheSameRuntimeIdentity() {
        OccurrenceStep step = factory.fromFlow(runtime(StepAmount.setsReps(2, 8), FlowGraphRun.State.AVAILABLE), "occurrence", 0);
        step = step.recordRepetitionResult(8);
        assertEquals("runtime-step", step.flowRunStepId);
        step = step.correctRepetitionResult(0, 7);
        assertEquals("runtime-step", step.flowRunStepId);
        OccurrenceStep carried = factory.carryForward(step, "next", 2, "occurrence");
        assertEquals("runtime-step", carried.flowRunStepId);
        assertEquals(7, carried.repetitionProgress.results.get(0).repetitions);
        assertEquals("runtime-step", carried.relocate("third", 3).flowRunStepId);
        assertEquals("runtime-step", carried.withCarryOrigin("first", CarryForwardReason.UNFINISHED_STEP).flowRunStepId);
        assertEquals("runtime-step", carried.recordRepetitionResult(8).reopen().flowRunStepId);
    }

    @Test public void blockedAndResourceWaitingActionsCannotAccidentallyCreateEmptyTodayOffers() {
        for (FlowGraphRun.State state : List.of(FlowGraphRun.State.BLOCKED, FlowGraphRun.State.WAITING_RESOURCE))
            assertThrows(IllegalArgumentException.class, () -> factory.fromFlow(runtime(StepAmount.none(), state), "occurrence", 0));
    }

    @Test public void normalStepsKeepNoRuntimeIdentityAndGraphIdentityCannotLoseItsSource() {
        OccurrenceStep ordinary = OccurrenceStep.rehydrate("id", "occ", 0, "Normal", false,
                StepPrescription.forAmount(StepAmount.none()), "", List.of(), null, "", null, CarryForwardReason.NONE);
        assertNull(ordinary.flowRunStepId);
        assertNull(ordinary.complete().reopen().relocate("other", 1).flowRunStepId);
        assertThrows(IllegalArgumentException.class, () -> OccurrenceStep.rehydrate("id", "occ", 0,
                "Invalid", false, ordinary.prescription, "", List.of(), null, "", null,
                CarryForwardReason.NONE, "runtime-step"));
    }

    private static FlowGraphRun.Step runtime(StepAmount amount, FlowGraphRun.State state) {
        return new FlowGraphRun.Step("runtime-step", new FlowGraphDefinition.Node("source-template",
                "Frozen name", StepPrescription.forAmount(amount), "Frozen note", FlowDelayPolicy.fixed(100)),
                state, null, null, null, 0);
    }
}
