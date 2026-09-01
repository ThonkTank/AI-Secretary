package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.ComboPolicy;
import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.HarvestOccurrence;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;
import de.thonktank.autosecretary.domain.usecase.RecordRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.CorrectRepetitionResult;
import de.thonktank.autosecretary.domain.usecase.FinishStepForToday;
import de.thonktank.autosecretary.domain.usecase.SettlePreviousPartialOccurrences;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class XpComboRobolectricTest {
    private final LocalDate today = LocalDate.of(2026, 8, 18);
    private AppDatabase database;
    private RoomRepositoryFixture repository;
    private MutableClock clock;
    private int id;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomRepositoryFixture(database);
        clock = new MutableClock(today);
    }

    @After public void tearDown() { database.close(); }

    @Test public void stepAndRoutineUseTheirOwnFactorsAndTodayUndoIsExact() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Collections.singletonList("Schritt")));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        repository.today.putCombo(new ComboProgress(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP, 6, today));
        repository.today.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 3, today));

        RewardReceipt stepReceipt = new ToggleStep(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id);
        assertEquals(2, stepReceipt.comboPointDelta);
        assertFalse(stepReceipt.transactionId.isEmpty());
        assertEquals(1, stepReceipt.bookings.size());
        assertEquals(RewardBooking.Target.VESSEL, stepReceipt.bookings.get(0).target);
        assertEquals(25, repository.today.rewardBookings(occurrence.id).stream()
                .filter(value -> step.id.equals(value.occurrenceStepId))
                .mapToInt(value -> value.xpDelta).sum());
        RewardReceipt harvest = new HarvestOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);
        assertEquals(2, harvest.comboPointDelta);
        assertEquals(50, harvest.xp);
        assertEquals(50, repository.today.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.today.findOccurrence(occurrence.id).state);
        assertEquals(today.plusDays(1), repository.catalog.findTask(occurrence.taskId).nextDueOn);

        RewardReceipt undo = new UndoOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);
        assertEquals(-2, undo.comboPointDelta);
        assertEquals(-50, undo.xp);
        assertEquals(RewardBooking.Kind.REVERSAL, undo.bookings.get(0).kind);
        assertEquals(harvest.bookings.get(0).id, undo.bookings.get(0).reversesBookingId);
        assertEquals(0, repository.today.xp());
        assertEquals(OccurrenceState.OPEN, repository.today.findOccurrence(occurrence.id).state);
        assertEquals(today.plusDays(1), repository.catalog.findTask(occurrence.taskId).nextDueOn);
        assertTrue(repository.steps.findOccurrenceStep(step.id).done);
        int afterOccurrenceUndo = repository.today.rewardBookings(occurrence.id).size();
        assertEquals(0, new UndoOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id).xp);
        assertEquals(afterOccurrenceUndo, repository.today.rewardBookings(occurrence.id).size());
        RewardReceipt stepUndo = new ToggleStep(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id);
        assertEquals(-2, stepUndo.comboPointDelta);
        assertEquals(-25, stepUndo.xp);
        assertEquals(false, repository.steps.findOccurrenceStep(step.id).done);
        assertEquals(6, repository.today.combo(step.comboOwnerId).points);
        assertEquals(3, repository.today.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
        assertThrows(RuntimeException.class, () -> repository.today.insertRewardBooking(
                stepReceipt.bookings.get(0).reverse("duplicate-reversal", "duplicate-transaction",
                        today)));

        RewardReceipt recompleted = new ToggleStep(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id);
        assertEquals(25, recompleted.xp);
        assertNotEquals(stepReceipt.transactionId, recompleted.transactionId);
        assertEquals(25, new RoomRepositoryFixture(database).today.rewardBookings(occurrence.id).stream()
                .filter(value -> value.target == RewardBooking.Target.VESSEL)
                .mapToInt(value -> value.xpDelta).sum());
        assertEquals(25, new de.thonktank.autosecretary.domain.usecase.LoadDashboard(
                repository.catalog, repository.steps, repository.today, repository.flows)
                .execute(today).tasks.get(0).earnedXp(step.id));
    }

    @Test public void restCompletionOnlyFillsBeforeSeparateHarvest() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Arrays.asList("Eins", "Zwei")));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);

        RewardReceipt fill = new CompleteRemainingSteps(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);

        assertEquals(20, fill.xp);
        assertEquals(0, repository.today.xp());
        assertEquals(OccurrenceState.OPEN, repository.today.findOccurrence(occurrence.id).state);
        for (OccurrenceStep step : repository.steps.occurrenceSteps(occurrence.id)) assertTrue(step.done);

        new HarvestOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);
        assertEquals(20, repository.today.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.today.findOccurrence(occurrence.id).state);
    }

    @Test public void widgetStyleCompletionFillsAndHarvestsAtomically() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Arrays.asList("Eins", "Zwei")));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);

        RewardReceipt receipt = new CompleteOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);

        assertEquals(20, repository.today.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.today.findOccurrence(occurrence.id).state);
        assertEquals(3, receipt.bookings.size());
        assertEquals(1, receipt.bookings.stream().map(value -> value.transactionId).distinct().count());
        assertEquals(receipt.transactionId, receipt.bookings.get(0).transactionId);
    }

    @Test public void lateCompletionKeepsXpPolicyAndEarnsTheSameComboGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        clock.date = today.minusDays(6);
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Verspätet", TaskSlot.MORNING, Recurrence.ONCE,
                        1, 0, Collections.emptyList()));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        clock.date = today;
        repository.today.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 5, clock.date));

        RewardReceipt receipt = new CompleteOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);

        assertEquals(60, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(7, repository.today.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
    }

    @Test public void lateRoutineStepEarnsTheSameComboGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        clock.date = today.minusDays(1);
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Collections.singletonList("Schritt")));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        clock.date = today;

        RewardReceipt receipt = new ToggleStep(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id);

        assertEquals(10, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(2, repository.today.combo(step.comboOwnerId).points);
    }

    @Test public void onTimeSingleTaskUsesOneFactorAndConfiguredDefaultGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(
                TaskDefinition.basic("Heute", TaskSlot.MORNING, Recurrence.ONCE,
                        1, 0, Collections.emptyList()));
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence occurrence = repository.today.openOccurrences().get(0);

        RewardReceipt receipt = new CompleteOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);

        assertEquals(10, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(2, repository.today.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
    }

    @Test public void repetitionXpTracksActualRatioAndCorrectionsAgainstFrozenPlan() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        createQuantitativeTask(ids, StepAmount.repetitions(10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        RecordRepetitionResult record = new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock);
        CorrectRepetitionResult correct = new CorrectRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock);

        assertEquals(5, record.execute(step.id, 5).xp);
        assertEquals(2, repository.today.combo(step.comboOwnerId).points);
        assertEquals(10, repository.today.rewardBookings(occurrence.id).get(0).plannedXp.intValue());

        assertEquals(10, correct.execute(step.id, 0, 15).xp);
        assertEquals(15, vesselXp(occurrence.id, step.id));
        assertTrue(repository.today.rewardBookings(occurrence.id).stream()
                .anyMatch(value -> value.kind == RewardBooking.Kind.STEP_ADJUSTMENT));

        assertEquals(-15, correct.execute(step.id, 0, 0).xp);
        assertEquals(0, vesselXp(occurrence.id, step.id));
        assertEquals(0, repository.today.combo(step.comboOwnerId).points);
    }

    @Test public void setXpUsesSumOfActualsAndMayExceedFullPlan() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        createQuantitativeTask(ids, StepAmount.setsReps(3, 10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        RecordRepetitionResult record = new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock);

        assertEquals(2, record.execute(step.id, 5).xp);
        assertEquals(3, record.execute(step.id, 10).xp);
        assertEquals(7, record.execute(step.id, 20).xp);
        assertEquals(12, vesselXp(occurrence.id, step.id));
        assertTrue(repository.steps.findOccurrenceStep(step.id).done);
        de.thonktank.autosecretary.presentation.today.FocusTaskUiModel focus =
                new de.thonktank.autosecretary.presentation.DashboardUiMapper(
                        new de.thonktank.autosecretary.presentation.AndroidUiTextProvider(
                                ApplicationProvider.getApplicationContext()))
                        .map(new de.thonktank.autosecretary.domain.usecase.LoadDashboard(
                                repository.catalog, repository.steps, repository.today,
                                repository.flows)
                                .execute(today), today).focus;
        assertEquals(12, focus.reward.resultXp);
        assertEquals(12, focus.vessel.earnedXp);
        assertEquals(10, focus.vessel.plannedXp);
    }

    @Test public void correctionKeepsTheComboPolicyOfTheFirstPositiveBooking() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        createQuantitativeTask(ids, StepAmount.repetitions(10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        ComboPolicy[] selected = {new ComboPolicy(0, 1,
                ComboDecayTrigger.DAILY_OVERDUE)};
        ComboPolicySource source = () -> selected[0];

        assertEquals(5, new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock, source)
                .execute(step.id, 5).xp);
        selected[0] = new ComboPolicy(4, 1, ComboDecayTrigger.DAILY_OVERDUE);
        assertEquals(1, new CorrectRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock, source)
                .execute(step.id, 0, 6).xp);
        assertEquals(0, repository.today.combo(step.comboOwnerId).points);
        assertEquals(6, vesselXp(occurrence.id, step.id));
    }

    @Test public void explicitZeroCompletesWithoutXpButResolvesTheScheduledObligation() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        createQuantitativeTask(ids, StepAmount.repetitions(10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);

        assertEquals(0, new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id, 0).xp);
        assertTrue(repository.steps.findOccurrenceStep(step.id).done);
        assertEquals(OccurrenceState.COMPLETED,
                repository.today.findOccurrence(occurrence.id).state);
        assertTrue(repository.today.rewardBookings(occurrence.id).isEmpty());
        assertEquals(0, repository.today.combo(step.comboOwnerId).points);
        assertTrue(repository.today.comboObligations().stream()
                .anyMatch(value -> step.comboOwnerId.equals(value.ownerId)
                        && value.resolvedOn != null));
        assertTrue(repository.today.comboObligations().stream()
                .anyMatch(value -> ComboProgress.taskOwner(occurrence.taskId).equals(value.ownerId)
                        && value.resolvedOn != null));
    }

    @Test public void finishForTodayPreservesPartialActualsAndRestFillsOnlyMissingSlots() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        createQuantitativeTask(ids, StepAmount.setsReps(3, 10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id, 5);

        new FinishStepForToday(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ComboPolicySource.defaults()).execute(step.id);
        OccurrenceStep early = repository.steps.findOccurrenceStep(step.id);
        assertTrue(early.done);
        assertEquals(Collections.singletonList(5),
                early.repetitionProgress.repetitions());

        new CorrectRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id, 0, 6);
        assertEquals(2, vesselXp(occurrence.id, step.id));

        // A fresh occurrence proves that "Rest erledigen" retains real input and fills the rest.
        new HarvestOccurrence(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(occurrence.id);
        clock.date = today.plusDays(1);
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
        Occurrence next = repository.today.openOccurrences().stream()
                .filter(value -> value.scheduledOn.equals(clock.date)).findFirst().get();
        OccurrenceStep nextStep = repository.steps.occurrenceSteps(next.id).get(0);
        new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(nextStep.id, 5);
        new CompleteRemainingSteps(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(next.id);
        assertEquals(Arrays.asList(5, 10, 10), repository.steps.findOccurrenceStep(nextStep.id)
                .repetitionProgress.repetitions());
        assertEquals(13, vesselXp(next.id, nextStep.id));
    }

    @Test public void nextDayAutoHarvestsPositivePartialWorkExactlyOnce() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        clock.date = today.minusDays(1);
        createQuantitativeTask(ids, StepAmount.setsReps(3, 10));
        Occurrence occurrence = repository.today.openOccurrences().get(0);
        OccurrenceStep step = repository.steps.occurrenceSteps(occurrence.id).get(0);
        new RecordRepetitionResult(repository.catalog, repository.steps, repository.today, repository.transactions, clock).execute(step.id, 5);
        assertEquals(0, repository.today.xp());

        clock.date = today;
        SettlePreviousPartialOccurrences settle = new SettlePreviousPartialOccurrences(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ComboPolicySource.defaults());
        assertTrue(settle.execute());
        assertEquals(2, repository.today.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.today.findOccurrence(occurrence.id).state);
        assertEquals(Collections.singletonList(5), repository.steps.findOccurrenceStep(step.id)
                .repetitionProgress.repetitions());
        assertFalse(settle.execute());
        assertEquals(2, repository.today.xp());
    }

    private void createQuantitativeTask(
            de.thonktank.autosecretary.domain.usecase.IdGenerator ids, StepAmount amount) {
        TaskStepDefinition step = de.thonktank.autosecretary.testing.StepTestFixtures.definition(null, 0, "Menge", 0, amount, "");
        TaskDefinition task = new TaskDefinition("Quantitativ", 10, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                null, null, null, null, "", Collections.singletonList(step));
        new CreateTask(repository.catalog, repository.steps, repository.today, repository.transactions, clock, ids).execute(task);
        new MaterializeDueOccurrences(repository.catalog, repository.steps, repository.today, repository.flows, repository.transactions, clock, ids).execute();
    }

    private int vesselXp(String occurrenceId, String stepId) {
        return repository.today.rewardBookings(occurrenceId).stream()
                .filter(value -> stepId.equals(value.occurrenceStepId)
                        && value.target == RewardBooking.Target.VESSEL)
                .mapToInt(value -> value.xpDelta).sum();
    }

    private static final class MutableClock implements Clock {
        LocalDate date;
        MutableClock(LocalDate date) { this.date = date; }
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
