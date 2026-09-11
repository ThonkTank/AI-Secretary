package de.thonktank.autosecretary;

import static org.junit.Assert.*;
import de.thonktank.autosecretary.domain.model.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class FlowTaskKindTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);

    @Test public void ordinaryCreationAndCopiesStayOrdinary() {
        Task task = Task.create(TaskId.of("task"), definition("Normal"), DATE, 10);
        assertEquals(TaskKind.TASK, task.kind);
        assertEquals(TaskKind.TASK, task.edit("Neu", 20).kind);
        assertSame(task, task.withKind(TaskKind.TASK));
        assertThrows(IllegalArgumentException.class, () -> task.withKind(null));
    }

    @Test public void flowIdentitySurvivesEveryMetadataCopyWithoutDependingOnStepCount() {
        Task task = Task.create(TaskId.of("flow"), definition("Ablauf"), DATE, 10).withKind(TaskKind.FLOW);
        for (Task changed : Arrays.asList(task.edit("Neu", 20), task.withCatalogOrder(30),
                task.editDefinition(definition("Bearbeitet"), 40),
                task.editDefinition(definition("Bearbeitet"), 40, DATE.plusDays(1)),
                task.afterPlanning(DATE.plusDays(2), 1),
                task.withOccurrenceState(true, DATE.plusDays(3), DATE, DATE, true),
                task.closeCondition(DATE), task.reopenCondition())) {
            assertEquals(TaskKind.FLOW, changed.kind);
            assertEquals(task.id, changed.id);
            assertEquals(task.cadenceAnchorOn, changed.cadenceAnchorOn);
        }
    }

    @Test public void markingAFlowDoesNotResetAnExhaustedBoundOrCadenceCursor() {
        Task original = Task.restore(TaskId.of("bound"), "Ablauf", Recurrence.INTERVAL, 7, 0,
                false, "", false, true, DATE.plusDays(7), DATE.minusDays(1), DATE,
                DATE.minusDays(30), 456, true, 12, TaskBoundKind.N_TIMES, null, null,
                0, null, "Notiz", MissedOccurrenceMode.ACCUMULATE);
        Task flow = original.withKind(TaskKind.FLOW);
        assertEquals(original.remainingCount, flow.remainingCount);
        assertEquals(original.nextDueOn, flow.nextDueOn);
        assertEquals(original.cadenceAnchorOn, flow.cadenceAnchorOn);
        assertEquals(original.archived, flow.archived);
        assertEquals(original.hasCompletedOccurrence, flow.hasCompletedOccurrence);
        assertEquals(original.note, flow.note);
        assertEquals(original.missedOccurrenceMode, flow.missedOccurrenceMode);
    }

    private TaskDefinition definition(String title) {
        return TaskDefinition.basic(title, TaskSlot.MORNING, Recurrence.DAILY, 1, 0, Collections.singletonList("Schritt"));
    }
}
