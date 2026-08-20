package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.HarvestOccurrence;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;
import de.thonktank.autosecretary.testing.InMemoryTaskRepository;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure domain/use-case coverage: no Android runtime and no Room database. */
public final class CompletionInMemoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);
    private final MutableClock clock = new MutableClock(TODAY);
    private InMemoryTaskRepository repository;
    private int id;

    @Before public void setUp() { repository = new InMemoryTaskRepository(); }

    @Test public void completionUndoAndRecompletionUseExactImmutableBookingsAndSchedule() {
        Occurrence occurrence = dailyRoutine("Routine", "Eins", "Zwei");
        OccurrenceStep first = repository.occurrenceSteps(occurrence.id).get(0);
        repository.putCombo(new ComboProgress(first.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP, 6, TODAY));
        repository.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 3, TODAY));

        RewardReceipt completed = new CompleteOccurrence(repository, clock).execute(occurrence.id);

        assertEquals(3, completed.bookings.size());
        assertEquals(1, completed.bookings.stream().map(value -> value.transactionId)
                .distinct().count());
        assertEquals(70, repository.xp());
        assertEquals(OccurrenceState.COMPLETED,
                repository.findOccurrence(occurrence.id).state);
        assertEquals(TODAY.plusDays(1), repository.findTask(occurrence.taskId).nextDueOn);
        assertEquals(3, repository.rewardBookings(occurrence.id).size());

        RewardReceipt undone = new UndoOccurrence(repository, clock).execute(occurrence.id);

        assertEquals(RewardBooking.Kind.REVERSAL, undone.bookings.get(0).kind);
        assertEquals(completed.bookings.get(2).id, undone.bookings.get(0).reversesBookingId);
        assertEquals(-70, undone.xp);
        assertEquals(0, repository.xp());
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(occurrence.id).state);
        assertEquals(TODAY.plusDays(1), repository.findTask(occurrence.taskId).nextDueOn);
        assertTrue(repository.findOccurrenceStep(first.id).done);
        assertEquals(4, repository.rewardBookings(occurrence.id).size());
        assertEquals(0, new UndoOccurrence(repository, clock).execute(occurrence.id).xp);

        RewardReceipt recompleted = new CompleteOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(70, recompleted.xp);
        assertNotEquals(completed.transactionId, recompleted.transactionId);
        assertEquals(70, repository.xp());
        assertEquals(5, repository.rewardBookings(occurrence.id).size());
    }

    @Test public void stepUndoKeepsOtherRewardsAndCanBeAppliedOnlyOnce() {
        Occurrence occurrence = dailyRoutine("Training", "Mobilisieren");
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        RewardReceipt earned = new ToggleStep(repository, clock).execute(step.id);
        assertEquals(10, earned.xp);
        assertTrue(repository.findOccurrenceStep(step.id).done);

        RewardReceipt reversed = new ToggleStep(repository, clock).execute(step.id);
        assertEquals(-10, reversed.xp);
        assertEquals(earned.bookings.get(0).id, reversed.bookings.get(0).reversesBookingId);
        assertFalse(repository.findOccurrenceStep(step.id).done);
        assertEquals(2, repository.rewardBookings(occurrence.id).size());
        assertEquals(10, new ToggleStep(repository, clock).execute(step.id).xp);
        assertEquals(10, repository.rewardBookings(occurrence.id).stream()
                .mapToInt(value -> value.xpDelta).sum());
    }

    @Test public void arbitraryTodayConditionUndoReopensTheConditionAndProjection() {
        create("Vorhaben", TaskSlot.LATER, Recurrence.ONCE, Collections.emptyList(), true,
                "Vertrag unterschrieben");
        Task task = repository.allTasks().get(0);

        RewardReceipt closed = new CloseOngoingTask(repository, clock).execute(task.id);
        Occurrence occurrence = repository.occurrences(task.id).get(0);
        assertEquals(10, closed.xp);
        assertTrue(repository.findTask(task.id).conditionDone);
        assertTrue(repository.findTask(task.id).archived);

        RewardReceipt undone = new UndoOccurrence(repository, clock).execute(occurrence.id);
        Task reopened = repository.findTask(task.id);
        assertEquals(-10, undone.xp);
        assertFalse(reopened.conditionDone);
        assertFalse(reopened.archived);
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(occurrence.id).state);
    }

    @Test public void transactionRollbackRestoresEveryProjectionAndLedgerEntry() {
        Occurrence occurrence = dailyRoutine("Rollback", "Schritt");
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);

        assertThrows(IllegalStateException.class, () -> repository.inTransaction(() -> {
            new ToggleStep(repository, clock).execute(step.id);
            repository.setXp(99);
            throw new IllegalStateException("rollback");
        }));

        assertFalse(repository.findOccurrenceStep(step.id).done);
        assertEquals(0, repository.xp());
        assertTrue(repository.rewardBookings(occurrence.id).isEmpty());
        assertTrue(repository.combos().isEmpty());
    }

    @Test public void rolloverCreatesTodaysInstanceWithUnfinishedAndFreshStepsOnce() {
        Occurrence yesterday = dailyRoutine("Tagesroutine", "Duschen", "Anziehen");
        OccurrenceStep finished = repository.occurrenceSteps(yesterday.id).get(0);
        new ToggleStep(repository, clock).execute(finished.id);

        clock.set(TODAY.plusDays(1));
        new MaterializeDueOccurrences(repository, clock, this::nextId).execute();

        assertEquals(OccurrenceState.MISSED, repository.findOccurrence(yesterday.id).state);
        List<Occurrence> open = repository.openOccurrences();
        assertEquals(1, open.size());
        assertEquals(TODAY.plusDays(1), open.get(0).scheduledOn);
        assertEquals(Arrays.asList("Anziehen", "Duschen"), texts(open.get(0).id));
        assertEquals(0, repository.rewardBookings(open.get(0).id).size());
        new MaterializeDueOccurrences(repository, clock, this::nextId).execute();
        assertEquals(1, repository.openOccurrences().size());
        assertEquals(2, repository.occurrenceSteps(open.get(0).id).size());
    }

    @Test public void partialHarvestClosesOccurrenceAndCarriesOpenStepNextDay() {
        Occurrence yesterday = dailyRoutine("Teilernte", "Erster", "Zweiter");
        OccurrenceStep finished = repository.occurrenceSteps(yesterday.id).get(0);
        new ToggleStep(repository, clock).execute(finished.id);

        assertEquals(10, new HarvestOccurrence(repository, clock).execute(yesterday.id).xp);
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(yesterday.id).state);
        assertEquals(10, repository.xp());

        clock.set(TODAY.plusDays(1));
        new MaterializeDueOccurrences(repository, clock, this::nextId).execute();
        assertEquals(1, repository.openOccurrences().size());
        assertEquals(Arrays.asList("Zweiter", "Erster"),
                texts(repository.openOccurrences().get(0).id));
    }

    @Test(timeout = 10_000L)
    public void dashboardRemainsDeterministicUnderManyTasksStepsCombosAndHistory() {
        final int taskCount = 240;
        final int historyPerTask = 20;
        for (int taskIndex = 0; taskIndex < taskCount; taskIndex++) {
            TaskId taskId = TaskId.of("stress-task-" + taskIndex);
            Task task = Task.create(taskId, "Aufgabe " + taskIndex, TaskSlot.values()[
                            taskIndex % TaskSlot.values().length], Recurrence.DAILY, 1, 0,
                    false, "", TODAY, taskIndex);
            repository.insertTask(task);
            for (int history = 0; history < historyPerTask; history++) {
                LocalDate date = TODAY.minusDays(history + 1L);
                repository.insertOccurrence(new Occurrence(taskId.value + "-history-" + history,
                        taskId, date, task.slot, OccurrenceState.COMPLETED, history, date));
            }
            Occurrence open = new Occurrence(taskId.value + "-open", taskId, TODAY, task.slot,
                    OccurrenceState.OPEN, taskIndex, null);
            repository.insertOccurrence(open);
            for (int step = 0; step < 12; step++)
                repository.insertOccurrenceSteps(Collections.singletonList(new OccurrenceStep(
                        open.id + "-step-" + step, open.id, step, "Schritt " + step, false)));
            repository.putCombo(new ComboProgress(ComboProgress.taskOwner(taskId), taskId,
                    ComboProgress.Kind.TASK, taskIndex % 8, TODAY));
        }

        Dashboard dashboard = new LoadDashboard(repository).execute(TODAY);

        assertEquals(taskCount, dashboard.tasks.size());
        assertEquals(taskCount, dashboard.combos.size());
        assertEquals(taskCount * (historyPerTask + 1), repository.allOccurrences().size());
        assertEquals(12, dashboard.tasks.get(0).steps.size());
    }

    private Occurrence dailyRoutine(String title, String... steps) {
        create(title, TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList(steps), false, "");
        new MaterializeDueOccurrences(repository, clock, this::nextId).execute();
        return repository.openOccurrences().get(0);
    }

    private void create(String title, TaskSlot slot, Recurrence recurrence,
                        java.util.List<String> steps, boolean ongoing, String condition) {
        new CreateTask(repository, clock, this::nextId, new TaskOrdering()).execute(title, slot,
                recurrence, 1, 0, steps, ongoing, condition);
    }

    private String nextId() { return "memory-id-" + ++id; }

    private java.util.List<String> texts(String occurrenceId) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId)) result.add(step.text);
        return result;
    }

    private static final class MutableClock implements Clock {
        private LocalDate date;
        MutableClock(LocalDate date) { this.date = date; }
        void set(LocalDate date) { this.date = date; }
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
