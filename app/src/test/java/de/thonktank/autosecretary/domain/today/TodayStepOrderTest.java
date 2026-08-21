package de.thonktank.autosecretary.domain.today;

import static org.junit.Assert.assertEquals;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.TaskId;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class TodayStepOrderTest {
    private final Occurrence open = new Occurrence("today", TaskId.of("task"),
            LocalDate.of(2026, 8, 21), OccurrenceState.OPEN, 0, null);
    private final List<OccurrenceStep> mixed = Arrays.asList(
            step("a", "today", 0, false), step("done-a", "today", 1, true),
            step("b", "today", 2, false), step("done-b", "today", 3, true),
            step("c", "today", 4, false));

    @Test public void movesToBeginningAndPreservesEveryCompletedSlot() {
        TodayStepMoveResult result = move(open, mixed, "c", "a", mixed.get(0));

        assertEquals(TodayStepMoveResult.Status.MOVED, result.status);
        assertEquals(Arrays.asList("c", "done-a", "a", "done-b", "b"),
                ids(result.orderedSteps));
        assertEquals(Arrays.asList("c", "a", "b"), result.openStepIds);
        assertEquals(Arrays.asList("c", "a", "b"), updateIds(result));
    }

    @Test public void nullTargetMovesToEndAndCurrentEndIsNoOp() {
        TodayStepMoveResult moved = move(open, mixed, "a", null, null);
        assertEquals(Arrays.asList("b", "done-a", "c", "done-b", "a"),
                ids(moved.orderedSteps));
        assertEquals(TodayStepMoveResult.Status.MOVED, moved.status);
        assertEquals(TodayStepMoveResult.Status.NO_CHANGE,
                move(open, mixed, "c", null, null).status);
    }

    @Test public void reportsDoneClosedInvalidAndCrossOccurrenceTargets() {
        assertEquals(TodayStepMoveResult.Status.STEP_ALREADY_DONE,
                move(open, mixed, "done-a", null, null).status);
        Occurrence closed = open.complete(LocalDate.of(2026, 8, 21));
        assertEquals(TodayStepMoveResult.Status.OCCURRENCE_CLOSED,
                move(closed, mixed, "a", null, null).status);
        assertEquals(TodayStepMoveResult.Status.INVALID_TARGET,
                move(open, mixed, "a", "missing", null).status);
        OccurrenceStep other = step("other", "other-occurrence", 0, false);
        assertEquals(TodayStepMoveResult.Status.TARGET_IN_OTHER_OCCURRENCE,
                move(open, mixed, "a", other.id, other).status);
        assertEquals(TodayStepMoveResult.Status.INVALID_TARGET,
                move(open, mixed, "a", "done-a", mixed.get(1)).status);
    }

    private static OccurrenceStep step(String id, String occurrence, int position, boolean done) {
        return new OccurrenceStep(id, occurrence, position, id, done);
    }

    private static TodayStepMoveResult move(Occurrence occurrence, List<OccurrenceStep> steps,
                                            String movingId, String beforeId,
                                            OccurrenceStep target) {
        return TodayStepOrder.move(new TodayOccurrenceSnapshot(occurrence, steps, target),
                movingId, beforeId);
    }

    private static List<String> ids(List<OccurrenceStep> values) {
        List<String> result = new java.util.ArrayList<>();
        for (OccurrenceStep value : values) result.add(value.id);
        return result;
    }

    private static List<String> updateIds(TodayStepMoveResult result) {
        List<String> values = new java.util.ArrayList<>();
        for (TodayStepPositionUpdate update : result.positionUpdates) values.add(update.stepId);
        return values;
    }
}
