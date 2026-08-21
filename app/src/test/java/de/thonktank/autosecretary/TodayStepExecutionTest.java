package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.AdvanceTodayStep;
import de.thonktank.autosecretary.domain.usecase.MoveTodayStep;
import de.thonktank.autosecretary.testing.InMemoryExecutionRepository;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TodayStepExecutionTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);
    private InMemoryExecutionRepository repository;
    private MutableClock clock;

    @Before public void setUp() {
        repository = new InMemoryExecutionRepository();
        clock = new MutableClock();
        TaskId task = TaskId.of("routine");
        repository.insertOccurrence(new Occurrence("today", task, TODAY, TaskSlot.MORNING,
                OccurrenceState.OPEN, 1, null));
        repository.insertOccurrenceSteps(Arrays.asList(
                step("first", 0, "Erster", false, StepAmount.none(), Collections.emptyList()),
                step("done", 1, "Fertig", true, StepAmount.none(), Collections.emptyList()),
                step("sets", 2, "Sätze", false, StepAmount.setsReps(3, 12),
                        Collections.emptyList()),
                step("last", 3, "Letzter", false, StepAmount.none(), Collections.emptyList())));
    }

    @Test public void plannedSetIsRecordedAndIncompleteFutureStepMovesToFirstOpenSlot() {
        AdvanceTodayStep advance = new AdvanceTodayStep(repository, clock);

        assertEquals(0, advance.execute("sets").xp);

        OccurrenceStep changed = repository.findOccurrenceStep("sets");
        assertFalse(changed.done);
        assertEquals(Collections.singletonList(12), changed.repetitionProgress.actualRepetitions);
        assertEquals(Arrays.asList("sets", "done", "first", "last"), ids());

        advance.execute("sets");
        assertEquals(Arrays.asList(12, 12), repository.findOccurrenceStep("sets")
                .repetitionProgress.actualRepetitions);
        assertEquals(10, advance.execute("sets").xp);
        assertTrue(repository.findOccurrenceStep("sets").done);
        assertEquals(Arrays.asList(12, 12, 12), repository.findOccurrenceStep("sets")
                .repetitionProgress.actualRepetitions);
    }

    @Test public void todayMoveReordersOnlyOpenSlotsAndPreservesCompletedSlot() {
        MoveTodayStep move = new MoveTodayStep(repository);

        assertTrue(move.execute("last", "first"));

        assertEquals(Arrays.asList("last", "done", "first", "sets"), ids());
        assertEquals(1, repository.findOccurrenceStep("done").position);
        assertFalse(move.execute("done", null));
    }

    @Test public void plainFutureStepCompletesImmediately() {
        assertEquals(10, new AdvanceTodayStep(repository, clock).execute("last").xp);
        assertTrue(repository.findOccurrenceStep("last").done);
    }

    private OccurrenceStep step(String id, int position, String text, boolean done,
                                StepAmount amount, List<Integer> actual) {
        return new OccurrenceStep(id, "today", position, text, done, amount, "", actual,
                "template-" + id, "step:template-" + id);
    }

    private List<String> ids() {
        List<String> result = new java.util.ArrayList<>();
        for (OccurrenceStep step : repository.occurrenceSteps("today")) result.add(step.id);
        return result;
    }

    private static final class MutableClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
