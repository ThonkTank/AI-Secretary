package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.CompletionStateMachine;
import de.thonktank.autosecretary.domain.usecase.RewardCalculator;
import de.thonktank.autosecretary.domain.usecase.ScheduleProjector;

import org.junit.Test;

import java.time.LocalDate;

public final class CompletionArchitectureTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Test public void stateMachineTransitionsWithoutPersistenceOrRewardKnowledge() {
        Occurrence occurrence = occurrence("occurrence", OccurrenceState.OPEN, null,
                OccurrenceKind.SCHEDULED, TODAY);
        OccurrenceStep step = new OccurrenceStep("step", occurrence.id, 0, "Schritt", false);
        CompletionStateMachine states = new CompletionStateMachine();

        OccurrenceStep done = states.completeStep(occurrence, step);
        assertTrue(done.done);
        assertFalse(states.reopenStep(occurrence, done).done);
        Occurrence completed = states.completeOccurrence(occurrence, TODAY);
        assertEquals(OccurrenceState.COMPLETED, completed.state);
        assertEquals(OccurrenceState.OPEN, states.reopenOccurrence(completed).state);
    }

    @Test public void calculatorIsPureAndClassifiesConditionExplicitly() {
        Task task = task(Recurrence.ONCE);
        ComboProgress combo = new ComboProgress(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK, 3, TODAY);
        Occurrence condition = occurrence("legacy-compatible-id", OccurrenceState.OPEN, null,
                OccurrenceKind.CONDITION, TODAY);
        RewardCalculator calculator = new RewardCalculator();

        RewardCalculator.StepReward step = calculator.step(combo, true);
        RewardCalculator.HarvestReward reward = calculator.harvest(task, condition, false,
                0, combo, TODAY);

        assertEquals(20, step.xp);
        assertEquals(1, step.requestedComboDelta);
        assertEquals(RewardBooking.Kind.CONDITION_COMPLETION, reward.kind);
        assertEquals(20, reward.xp);
        assertEquals(3, reward.requestedComboDelta);
        assertEquals(3, combo.points);
    }

    @Test public void scheduleProjectorDoesNotChangePlanningCursor() {
        Task task = task(Recurrence.DAILY);
        Occurrence latest = occurrence("done", OccurrenceState.COMPLETED, TODAY,
                OccurrenceKind.SCHEDULED, TODAY.minusDays(1));
        Occurrence reopened = latest.reopen();
        ScheduleProjector projector = new ScheduleProjector();

        Task afterCompletion = projector.project(task, new ScheduleProjector.Input(null, latest));
        assertEquals(TODAY, afterCompletion.nextDueOn);
        assertFalse(afterCompletion.archived);
        Task afterUndo = projector.project(afterCompletion,
                new ScheduleProjector.Input(reopened, null));
        assertEquals(TODAY, afterUndo.nextDueOn);
    }

    private static Task task(Recurrence recurrence) {
        return Task.create(TaskId.of("task"), "Aufgabe", TaskSlot.MORNING, recurrence,
                1, 0, false, "", TODAY, 1_024L);
    }

    private static Occurrence occurrence(String id, OccurrenceState state, LocalDate completed,
                                         OccurrenceKind kind, LocalDate scheduled) {
        return new Occurrence(id, TaskId.of("task"), scheduled, TaskSlot.MORNING, state,
                1, completed, kind);
    }
}
