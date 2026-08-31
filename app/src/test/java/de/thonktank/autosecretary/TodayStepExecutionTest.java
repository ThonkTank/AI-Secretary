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
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.usecase.AdvanceTodayStep;
import de.thonktank.autosecretary.domain.usecase.MoveTodayStep;
import de.thonktank.autosecretary.domain.usecase.RecordRepetitionResult;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
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
        repository.insertTemplates(Arrays.asList(
                template(task, "template-first", 0, "Erster"),
                template(task, "template-done", 1, "Fertig"),
                template(task, "template-sets", 2, "Sätze"),
                template(task, "template-last", 3, "Letzter")));
    }

    @Test public void plannedSetIsRecordedAndIncompleteFutureStepMovesToFirstOpenSlot() {
        AdvanceTodayStep advance = new AdvanceTodayStep(repository, repository, repository, repository, clock);

        AdvanceTodayStepResult firstAdvance = advance.execute("sets");
        assertEquals(AdvanceTodayStepResult.Status.PROGRESS_RECORDED, firstAdvance.status);
        assertEquals(Integer.valueOf(12), firstAdvance.recordedPlanValue);
        assertEquals(Arrays.asList("sets", "first", "last"), firstAdvance.openStepIds);
        assertEquals(4, firstAdvance.xp);

        OccurrenceStep changed = repository.findOccurrenceStep("sets");
        assertFalse(changed.done);
        assertEquals(Collections.singletonList(12), changed.repetitionProgress.actualRepetitions);
        assertEquals(Arrays.asList("sets", "done", "first", "last"), ids());

        advance.execute("sets");
        assertEquals(Arrays.asList(12, 12), repository.findOccurrenceStep("sets")
                .repetitionProgress.actualRepetitions);
        AdvanceTodayStepResult completed = advance.execute("sets");
        assertEquals(AdvanceTodayStepResult.Status.STEP_COMPLETED, completed.status);
        assertEquals(Integer.valueOf(12), completed.recordedPlanValue);
        assertEquals(Arrays.asList("first", "last"), completed.openStepIds);
        assertEquals(3, completed.xp);
        assertEquals(10, repository.rewardBookings("today").stream()
                .filter(value -> "sets".equals(value.occurrenceStepId))
                .mapToInt(value -> value.xpDelta).sum());
        assertTrue(repository.findOccurrenceStep("sets").done);
        assertEquals(Arrays.asList(12, 12, 12), repository.findOccurrenceStep("sets")
                .repetitionProgress.actualRepetitions);
    }

    @Test public void todayMoveReordersOnlyOpenSlotsAndPreservesCompletedSlot() {
        MoveTodayStep move = new MoveTodayStep(repository, repository);
        List<TaskStepDefinition> templatesBefore = templateDefinitions();

        assertEquals(de.thonktank.autosecretary.domain.today.TodayStepMoveResult.Status.MOVED,
                move.execute("last", "first").status);

        assertEquals(Arrays.asList("last", "done", "first", "sets"), ids());
        assertEquals(1, repository.findOccurrenceStep("done").position);
        assertEquals(templatesBefore, templateDefinitions());
        assertEquals(de.thonktank.autosecretary.domain.today.TodayStepMoveResult.Status
                        .STEP_ALREADY_DONE,
                move.execute("done", null).status);
    }

    @Test public void todayMovePreservesEveryCompletedSlotAndAnotherOccurrence() {
        TaskId task = TaskId.of("routine");
        repository.insertOccurrence(new Occurrence("future", task, TODAY.plusDays(1),
                TaskSlot.MORNING, OccurrenceState.OPEN, 2, null));
        repository.insertOccurrenceSteps(Arrays.asList(
                stepIn("future-a", "future", 0, "A", false),
                stepIn("future-b", "future", 1, "B", false)));
        repository.insertOccurrence(new Occurrence("mixed", task, TODAY, TaskSlot.MIDDAY,
                OccurrenceState.OPEN, 3, null));
        repository.insertOccurrenceSteps(Arrays.asList(
                stepIn("open-a", "mixed", 0, "A", false),
                stepIn("done-a", "mixed", 1, "B", true),
                stepIn("open-b", "mixed", 2, "C", false),
                stepIn("done-b", "mixed", 3, "D", true),
                stepIn("open-c", "mixed", 4, "E", false)));

        assertEquals(de.thonktank.autosecretary.domain.today.TodayStepMoveResult.Status.MOVED,
                new MoveTodayStep(repository, repository).execute("open-c", "open-a").status);

        assertEquals(Arrays.asList("open-c", "done-a", "open-a", "done-b", "open-b"),
                ids("mixed"));
        assertEquals(1, repository.findOccurrenceStep("done-a").position);
        assertEquals(3, repository.findOccurrenceStep("done-b").position);
        assertEquals(Arrays.asList("future-a", "future-b"), ids("future"));
    }

    @Test public void plainFutureStepCompletesImmediately() {
        AdvanceTodayStepResult result = new AdvanceTodayStep(repository, repository, repository, repository, clock).execute("last");
        assertEquals(AdvanceTodayStepResult.Status.STEP_COMPLETED, result.status);
        assertEquals(null, result.recordedPlanValue);
        assertEquals(10, result.xp);
        assertTrue(repository.findOccurrenceStep("last").done);
    }

    @Test public void incompleteRepetitionWriteImmediatelyRewardsTheActualRatio() {
        StepExecutionResult result = new RecordRepetitionResult(repository, repository, repository, repository, clock)
                .execute("sets", 11);

        assertEquals(StepExecutionResult.Status.RECORDED, result.status);
        assertTrue(result.changed());
        assertEquals(Collections.singletonList(11),
                result.step.repetitionProgress.actualRepetitions);
        assertEquals(4, result.rewardReceipt.xp);
        assertFalse(result.rewardReceipt.transactionId.isEmpty());
    }

    private OccurrenceStep step(String id, int position, String text, boolean done,
                                StepAmount amount, List<Integer> actual) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.occurrence(id, "today", position, text, done, amount, "", actual,
                "template-" + id, "step:template-" + id);
    }

    private OccurrenceStep stepIn(String id, String occurrenceId, int position,
                                  String text, boolean done) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.occurrence(id, occurrenceId, position, text, done,
                StepAmount.none(), "", Collections.emptyList(), "template-" + id,
                "step:template-" + id);
    }

    private TaskStepTemplate template(TaskId taskId, String id, int position, String text) {
        return de.thonktank.autosecretary.testing.StepTestFixtures.template(id, taskId, position, text);
    }

    private List<TaskStepDefinition> templateDefinitions() {
        List<TaskStepDefinition> result = new java.util.ArrayList<>();
        for (TaskStepTemplate template : repository.templates(TaskId.of("routine")))
            result.add(template.definition());
        return result;
    }

    private List<String> ids() {
        return ids("today");
    }

    private List<String> ids(String occurrenceId) {
        List<String> result = new java.util.ArrayList<>();
        for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId)) result.add(step.id);
        return result;
    }

    private static final class MutableClock implements Clock {
        @Override public LocalDate today() { return TODAY; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
