package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveResult;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.StepMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepTransferResult;
import de.thonktank.autosecretary.domain.steps.StepSwapRequest;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class AllTasksFeatureRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private AppDatabase database;
    private ApplicationTaskRepository repository;
    private SequenceIds ids;
    private Clock clock;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        repository = new RoomTaskRepository(database);
        ids = new SequenceIds();
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
    }

    @After public void tearDown() { database.close(); }

    @Test public void movingOneMultiTimePlacementKeepsTheOtherAndMovesOpenTodayRow() {
        create("Routine", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Schritt"));
        Task task = repository.allTasks().get(0);
        repository.putScheduleEntries(Arrays.asList(
                new TaskScheduleEntry(ids.nextId(), task.id, TaskSlot.EVENING, 2_048)));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        TaskScheduleEntry morning = repository.scheduleEntries(task.id).stream()
                .filter(value -> value.slot == TaskSlot.MORNING).findFirst().orElseThrow();

        new MoveScheduleEntry(repository).execute(move(morning.id, TaskSlot.MIDDAY, null));

        assertTrue(repository.scheduleEntries(task.id).stream()
                .anyMatch(value -> value.slot == TaskSlot.MIDDAY));
        assertTrue(repository.scheduleEntries(task.id).stream()
                .anyMatch(value -> value.slot == TaskSlot.EVENING));
        assertTrue(repository.openOccurrences().stream()
                .anyMatch(value -> value.slot == TaskSlot.MIDDAY));
        assertEquals(ScheduleMoveResult.REJECTED_DUPLICATE_SLOT,
                new MoveScheduleEntry(repository).execute(
                        move(morning.id, TaskSlot.EVENING, null)));
    }

    @Test public void reorderingOneSlotAlsoReordersItsOpenTodayRows() {
        create("Erste", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("A"));
        create("Zweite", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("B"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task first = repository.allTasks().stream()
                .filter(value -> value.title.equals("Erste")).findFirst().orElseThrow();
        Task second = repository.allTasks().stream()
                .filter(value -> value.title.equals("Zweite")).findFirst().orElseThrow();
        TaskScheduleEntry firstEntry = repository.scheduleEntries(first.id).get(0);
        TaskScheduleEntry secondEntry = repository.scheduleEntries(second.id).get(0);

        new MoveScheduleEntry(repository).execute(
                move(secondEntry.id, TaskSlot.MORNING, firstEntry.id));

        assertTrue(repository.scheduleEntries(second.id).get(0).displayOrder
                < repository.scheduleEntries(first.id).get(0).displayOrder);
        assertTrue(occurrence(second.id).sortOrder < occurrence(first.id).sortOrder);
    }

    @Test public void crossTaskStepMoveCarriesDoneStateRewardAndComboToMatchingOpenSheet() {
        create("Quelle", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Erledigt", "Bleibt"));
        create("Ziel", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Zielschritt"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task source = repository.allTasks().stream()
                .filter(value -> value.title.equals("Quelle")).findFirst().orElseThrow();
        Task target = repository.allTasks().stream()
                .filter(value -> value.title.equals("Ziel")).findFirst().orElseThrow();
        TaskStepTemplate moving = repository.templates(source.id).get(0);
        Occurrence sourceOccurrence = occurrence(source.id);
        Occurrence targetOccurrence = occurrence(target.id);
        OccurrenceStep snapshot = repository.occurrenceSteps(sourceOccurrence.id).stream()
                .filter(value -> moving.id.equals(value.sourceTemplateId)).findFirst().orElseThrow();
        de.thonktank.autosecretary.domain.model.RewardReceipt receipt =
                new ToggleStep(repository, clock).execute(snapshot.id);
        String bookingId = receipt.bookings.get(0).id;
        int xpBefore = repository.xp();
        int comboBefore = repository.combo("step:" + moving.id).points;

        assertEquals(StepTransferResult.DEFINITION_AND_TODAY_MOVED,
                new MoveTaskStep(repository).execute(new StepMoveRequest(
                        TaskStepId.of(moving.id), target.id, java.util.Optional.empty())));

        TaskStepTemplate moved = repository.templates(target.id).stream()
                .filter(value -> value.id.equals(moving.id)).findFirst().orElseThrow();
        assertNotNull(moved);
        assertEquals(target.id, repository.combo("step:" + moving.id).taskId);
        OccurrenceStep movedSnapshot = repository.findOccurrenceStep(snapshot.id);
        assertEquals(targetOccurrence.id, movedSnapshot.occurrenceId);
        assertTrue(movedSnapshot.done);
        assertTrue(repository.rewardBookings(targetOccurrence.id).stream()
                .anyMatch(value -> snapshot.id.equals(value.occurrenceStepId)));
        assertEquals("The immutable ledger row keeps its original occurrence",
                sourceOccurrence.id, database.tasks().ledgerRewardBooking(bookingId).occurrenceId);
        assertEquals(xpBefore, repository.xp());
        assertEquals(comboBefore, repository.combo("step:" + moving.id).points);
        assertEquals("step:" + moving.id,
                repository.combo("step:" + moving.id).ownerId);
    }

    @Test public void swappingStepsInsideOneTaskKeepsIdsAndReordersTheOpenSnapshot() {
        create("Routine", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Erster", "Zweiter"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task task = repository.allTasks().get(0);
        TaskStepTemplate first = repository.templates(task.id).get(0);
        TaskStepTemplate second = repository.templates(task.id).get(1);

        new SwapTaskSteps(repository).execute(new StepSwapRequest(
                TaskStepId.of(first.id), TaskStepId.of(second.id)));

        assertEquals(second.id, repository.templates(task.id).get(0).id);
        assertEquals(first.id, repository.templates(task.id).get(1).id);
        assertEquals(second.id, repository.occurrenceSteps(occurrence(task.id).id)
                .get(0).sourceTemplateId);
    }

    @Test public void moveWithoutMatchingTodaySlotLeavesTheSourceSnapshotUntouched() {
        create("Quelle", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Wandert"));
        create("Ziel", TaskSlot.EVENING, Recurrence.DAILY, Arrays.asList("Bleibt"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task source = repository.allTasks().stream()
                .filter(value -> value.title.equals("Quelle")).findFirst().orElseThrow();
        Task target = repository.allTasks().stream()
                .filter(value -> value.title.equals("Ziel")).findFirst().orElseThrow();
        TaskStepTemplate moving = repository.templates(source.id).get(0);
        Occurrence sourceOccurrence = occurrence(source.id);
        String snapshotId = repository.occurrenceSteps(sourceOccurrence.id).get(0).id;

        assertEquals(StepTransferResult.DEFINITION_ONLY_FOR_FUTURE,
                new MoveTaskStep(repository).execute(new StepMoveRequest(
                        TaskStepId.of(moving.id), target.id, java.util.Optional.empty())));

        assertTrue(repository.templates(source.id).isEmpty());
        assertEquals(moving.id, repository.templates(target.id).stream()
                .filter(value -> value.id.equals(moving.id)).findFirst().orElseThrow().id);
        assertEquals(sourceOccurrence.id,
                repository.findOccurrenceStep(snapshotId).occurrenceId);
    }

    @Test public void crossTaskSwapMovesOpenSnapshotsButNeverRewritesTheirRewardLedger() {
        create("Links", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("A"));
        create("Rechts", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("B"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task left = repository.allTasks().stream().filter(value -> value.title.equals("Links"))
                .findFirst().orElseThrow();
        Task right = repository.allTasks().stream().filter(value -> value.title.equals("Rechts"))
                .findFirst().orElseThrow();
        TaskStepTemplate first = repository.templates(left.id).get(0);
        TaskStepTemplate second = repository.templates(right.id).get(0);
        Occurrence leftOccurrence = occurrence(left.id);
        Occurrence rightOccurrence = occurrence(right.id);
        OccurrenceStep firstSnapshot = repository.occurrenceSteps(leftOccurrence.id).get(0);
        String bookingId = new ToggleStep(repository, clock).execute(firstSnapshot.id)
                .bookings.get(0).id;

        assertEquals(StepTransferResult.STEPS_SWAPPED,
                new SwapTaskSteps(repository).execute(new StepSwapRequest(
                        TaskStepId.of(first.id), TaskStepId.of(second.id))));

        assertEquals(rightOccurrence.id,
                repository.findOccurrenceStep(firstSnapshot.id).occurrenceId);
        assertEquals(leftOccurrence.id,
                database.tasks().ledgerRewardBooking(bookingId).occurrenceId);
        assertTrue(repository.rewardBookings(rightOccurrence.id).stream()
                .anyMatch(value -> value.id.equals(bookingId)));
    }

    @Test public void archivedTargetRejectsMoveWithoutChangingDefinitionsOrHistory() {
        create("Quelle", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("A"));
        create("Archiv", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("B"));
        Task source = repository.allTasks().stream().filter(value -> value.title.equals("Quelle"))
                .findFirst().orElseThrow();
        Task target = repository.allTasks().stream().filter(value -> value.title.equals("Archiv"))
                .findFirst().orElseThrow();
        repository.updateTask(target.withOccurrenceState(true, target.nextDueOn,
                target.lastScheduledOn, target.lastCompletedOn, target.hasCompletedOccurrence));
        TaskStepTemplate moving = repository.templates(source.id).get(0);

        assertEquals(StepTransferResult.REJECTED_ARCHIVED_TASK,
                new MoveTaskStep(repository).execute(new StepMoveRequest(
                        TaskStepId.of(moving.id), target.id, java.util.Optional.empty())));
        assertEquals(source.id, repository.findTemplate(moving.id).taskId);
    }

    @Test public void completedSnapshotRemainsHistoricalWhenDefinitionMoves() {
        create("Historie", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Alt"));
        create("Zukunft", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Neu"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task source = repository.allTasks().stream().filter(value -> value.title.equals("Historie"))
                .findFirst().orElseThrow();
        Task target = repository.allTasks().stream().filter(value -> value.title.equals("Zukunft"))
                .findFirst().orElseThrow();
        Occurrence historical = occurrence(source.id);
        OccurrenceStep snapshot = repository.occurrenceSteps(historical.id).get(0);
        repository.updateOccurrence(historical.complete(TODAY));
        TaskStepTemplate moving = repository.templates(source.id).get(0);
        Occurrence harvested = new Occurrence("harvested-history", source.id,
                TODAY.minusDays(1), TaskSlot.MORNING,
                de.thonktank.autosecretary.domain.model.OccurrenceState.OPEN, 1, null)
                .harvestedWithMissedSteps(TODAY.minusDays(1));
        OccurrenceStep harvestedSnapshot = new OccurrenceStep("harvested-step", harvested.id,
                0, moving.text, false, moving.amount, moving.note,
                java.util.Collections.emptyList(), moving.id, "step:" + moving.id);
        repository.insertOccurrence(harvested);
        repository.insertOccurrenceSteps(java.util.Collections.singletonList(harvestedSnapshot));

        assertEquals(StepTransferResult.DEFINITION_ONLY_FOR_FUTURE,
                new MoveTaskStep(repository).execute(new StepMoveRequest(
                        TaskStepId.of(moving.id), target.id, java.util.Optional.empty())));

        assertEquals(historical.id, repository.findOccurrenceStep(snapshot.id).occurrenceId);
        assertEquals(de.thonktank.autosecretary.domain.model.OccurrenceState.COMPLETED,
                repository.findOccurrence(historical.id).state);
        assertEquals(harvested.id,
                repository.findOccurrenceStep(harvestedSnapshot.id).occurrenceId);
        assertEquals(de.thonktank.autosecretary.domain.model.OccurrenceState
                        .HARVESTED_WITH_MISSED_STEPS,
                repository.findOccurrence(harvested.id).state);
    }

    @Test public void invalidTemplatePositionSequenceIsRejectedBeforeMutation() {
        create("Quelle", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("A", "B"));
        create("Ziel", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("C"));
        Task source = repository.allTasks().stream().filter(value -> value.title.equals("Quelle"))
                .findFirst().orElseThrow();
        Task target = repository.allTasks().stream().filter(value -> value.title.equals("Ziel"))
                .findFirst().orElseThrow();
        TaskStepTemplate moving = repository.templates(source.id).get(0);
        TaskStepTemplate invalid = repository.templates(source.id).get(1);
        repository.insertTemplates(java.util.Collections.singletonList(new TaskStepTemplate(
                invalid.id, source.id, 4, invalid.text, invalid.weekdayMask,
                invalid.amount, invalid.note)));

        assertEquals(StepTransferResult.REJECTED_INVALID_POSITION_SEQUENCE,
                new MoveTaskStep(repository).execute(new StepMoveRequest(
                        TaskStepId.of(moving.id), target.id, java.util.Optional.empty())));
        assertEquals(source.id, repository.findTemplate(moving.id).taskId);
        assertEquals(4, repository.findTemplate(invalid.id).position);
    }

    private Occurrence occurrence(TaskId id) {
        return repository.openOccurrences().stream().filter(value -> value.taskId.equals(id))
                .findFirst().orElseThrow();
    }

    private static ScheduleMoveRequest move(String id, TaskSlot slot, String before) {
        return new ScheduleMoveRequest(ScheduleEntryId.of(id), slot,
                java.util.Optional.ofNullable(before).map(ScheduleEntryId::of));
    }

    private void create(String title, TaskSlot slot, Recurrence recurrence,
                        java.util.List<String> steps) {
        new CreateTask(repository, repository, clock, ids).execute(
                TaskDefinition.basic(title, slot, recurrence, 1, 0, steps));
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "all-" + ++next; }
    }
}
