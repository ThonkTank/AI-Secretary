package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CompleteRemainingSteps;
import de.thonktank.autosecretary.domain.usecase.HarvestOccurrence;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;

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
    private ApplicationTaskRepository repository;
    private MutableClock clock;
    private int id;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        clock = new MutableClock(today);
    }

    @After public void tearDown() { database.close(); }

    @Test public void stepAndRoutineUseTheirOwnFactorsAndTodayUndoIsExact() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Collections.singletonList("Schritt")));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        repository.putCombo(new ComboProgress(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP, 6, today));
        repository.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 3, today));

        RewardReceipt stepReceipt = new ToggleStep(repository, clock).execute(step.id);
        assertEquals(2, stepReceipt.comboPointDelta);
        assertFalse(stepReceipt.transactionId.isEmpty());
        assertEquals(1, stepReceipt.bookings.size());
        assertEquals(RewardBooking.Target.VESSEL, stepReceipt.bookings.get(0).target);
        assertEquals(25, repository.rewardBookings(occurrence.id).stream()
                .filter(value -> step.id.equals(value.occurrenceStepId))
                .mapToInt(value -> value.xpDelta).sum());
        RewardReceipt harvest = new HarvestOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(2, harvest.comboPointDelta);
        assertEquals(50, harvest.xp);
        assertEquals(50, repository.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(occurrence.id).state);
        assertEquals(today.plusDays(1), repository.findTask(occurrence.taskId).nextDueOn);

        RewardReceipt undo = new UndoOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(-2, undo.comboPointDelta);
        assertEquals(-50, undo.xp);
        assertEquals(RewardBooking.Kind.REVERSAL, undo.bookings.get(0).kind);
        assertEquals(harvest.bookings.get(0).id, undo.bookings.get(0).reversesBookingId);
        assertEquals(0, repository.xp());
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(occurrence.id).state);
        assertEquals(today.plusDays(1), repository.findTask(occurrence.taskId).nextDueOn);
        assertTrue(repository.findOccurrenceStep(step.id).done);
        int afterOccurrenceUndo = repository.rewardBookings(occurrence.id).size();
        assertEquals(0, new UndoOccurrence(repository, clock).execute(occurrence.id).xp);
        assertEquals(afterOccurrenceUndo, repository.rewardBookings(occurrence.id).size());
        RewardReceipt stepUndo = new ToggleStep(repository, clock).execute(step.id);
        assertEquals(-2, stepUndo.comboPointDelta);
        assertEquals(-25, stepUndo.xp);
        assertEquals(false, repository.findOccurrenceStep(step.id).done);
        assertEquals(6, repository.combo(step.comboOwnerId).points);
        assertEquals(3, repository.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
        assertThrows(RuntimeException.class, () -> repository.insertRewardBooking(
                stepReceipt.bookings.get(0).reverse("duplicate-reversal", "duplicate-transaction",
                        today)));

        RewardReceipt recompleted = new ToggleStep(repository, clock).execute(step.id);
        assertEquals(25, recompleted.xp);
        assertNotEquals(stepReceipt.transactionId, recompleted.transactionId);
        assertEquals(25, new RoomTaskRepository(database).rewardBookings(occurrence.id).stream()
                .filter(value -> value.target == RewardBooking.Target.VESSEL)
                .mapToInt(value -> value.xpDelta).sum());
        assertEquals(25, new de.thonktank.autosecretary.domain.usecase.LoadDashboard(
                new RoomTaskRepository(database)).execute(today).tasks.get(0).earnedXp(step.id));
    }

    @Test public void restCompletionOnlyFillsBeforeSeparateHarvest() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Arrays.asList("Eins", "Zwei")));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);

        RewardReceipt fill = new CompleteRemainingSteps(repository, clock).execute(occurrence.id);

        assertEquals(20, fill.xp);
        assertEquals(0, repository.xp());
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(occurrence.id).state);
        for (OccurrenceStep step : repository.occurrenceSteps(occurrence.id)) assertTrue(step.done);

        new HarvestOccurrence(repository, clock).execute(occurrence.id);
        assertEquals(20, repository.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(occurrence.id).state);
    }

    @Test public void widgetStyleCompletionFillsAndHarvestsAtomically() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Arrays.asList("Eins", "Zwei")));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);

        RewardReceipt receipt = new CompleteOccurrence(repository, clock).execute(occurrence.id);

        assertEquals(20, repository.xp());
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(occurrence.id).state);
        assertEquals(3, receipt.bookings.size());
        assertEquals(1, receipt.bookings.stream().map(value -> value.transactionId).distinct().count());
        assertEquals(receipt.transactionId, receipt.bookings.get(0).transactionId);
    }

    @Test public void lateCompletionKeepsXpPolicyAndEarnsTheSameComboGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        clock.date = today.minusDays(6);
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Verspätet", TaskSlot.MORNING, Recurrence.ONCE,
                        1, 0, Collections.emptyList()));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        clock.date = today;
        repository.putCombo(new ComboProgress(ComboProgress.taskOwner(occurrence.taskId),
                occurrence.taskId, ComboProgress.Kind.TASK, 5, clock.date));

        RewardReceipt receipt = new CompleteOccurrence(repository, clock).execute(occurrence.id);

        assertEquals(60, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(7, repository.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
    }

    @Test public void lateRoutineStepEarnsTheSameComboGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        clock.date = today.minusDays(1);
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Routine", TaskSlot.MORNING, Recurrence.DAILY,
                        1, 0, Collections.singletonList("Schritt")));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        OccurrenceStep step = repository.occurrenceSteps(occurrence.id).get(0);
        clock.date = today;

        RewardReceipt receipt = new ToggleStep(repository, clock).execute(step.id);

        assertEquals(10, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(2, repository.combo(step.comboOwnerId).points);
    }

    @Test public void onTimeSingleTaskUsesOneFactorAndConfiguredDefaultGain() {
        de.thonktank.autosecretary.domain.usecase.IdGenerator ids = () -> "id-" + ++id;
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic("Heute", TaskSlot.MORNING, Recurrence.ONCE,
                        1, 0, Collections.emptyList()));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);

        RewardReceipt receipt = new CompleteOccurrence(repository, clock).execute(occurrence.id);

        assertEquals(10, receipt.xp);
        assertEquals(2, receipt.comboPointDelta);
        assertEquals(2, repository.combo(ComboProgress.taskOwner(occurrence.taskId)).points);
    }

    private static final class MutableClock implements Clock {
        LocalDate date;
        MutableClock(LocalDate date) { this.date = date; }
        @Override public LocalDate today() { return date; }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }
}
