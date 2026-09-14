package de.thonktank.autosecretary.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Collections;

import org.junit.Test;

public final class StepPrescriptionTest {
    @Test public void nonSetStepsRejectRest() {
        assertThrows(IllegalArgumentException.class, () -> new StepPrescription(
                StepAmount.duration(30), RestTimerPolicy.custom(60)));
    }



    @Test public void materializedCopiesDoNotFollowLaterTemplatePrescription() {
        StepPrescription original = new StepPrescription(StepAmount.setsReps(3, 10),
                RestTimerPolicy.custom(75));
        TaskStepTemplate template = new TaskStepTemplate("step", TaskId.of("task"), 0,
                "Rudern", 0, 0, original, "", StepActivationKind.SCHEDULED);
        OccurrenceStep occurrence = OccurrenceStep.rehydrate("occ-step", "occ", 0, "Rudern", false,
                template.prescription, "", Collections.emptyList(), template.id,
                "step:" + template.id, null, CarryForwardReason.NONE);
        FlowRunStepSnapshot snapshot = FlowRunStepSnapshot.rehydrate("flow-step", "run", 0,
                template.id, template.text, template.prescription, "", null, null);

        StepPrescription changed = new StepPrescription(StepAmount.setsReps(4, 8),
                RestTimerPolicy.custom(90));
        template = new TaskStepTemplate(template.id, template.taskId, template.position,
                template.text, template.weekdayMask, template.intervalDays, changed, template.note, template.activationKind);

        assertNotEquals(template.prescription, occurrence.prescription);
        assertEquals(original, occurrence.prescription);
        assertEquals(original, snapshot.prescription);
    }
}
