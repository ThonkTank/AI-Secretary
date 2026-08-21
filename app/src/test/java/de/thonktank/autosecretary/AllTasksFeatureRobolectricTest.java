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
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.ScheduleEntryId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.usecase.OrganizeTaskStep;
import de.thonktank.autosecretary.domain.usecase.ScheduleMoveResult;
import de.thonktank.autosecretary.domain.usecase.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.usecase.StepMoveRequest;
import de.thonktank.autosecretary.domain.usecase.StepSwapRequest;
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
    private TaskRepository repository;
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

        new MoveScheduleEntry(repository, clock).execute(move(morning.id, TaskSlot.MIDDAY, null));

        assertTrue(repository.scheduleEntries(task.id).stream()
                .anyMatch(value -> value.slot == TaskSlot.MIDDAY));
        assertTrue(repository.scheduleEntries(task.id).stream()
                .anyMatch(value -> value.slot == TaskSlot.EVENING));
        assertTrue(repository.openOccurrences().stream()
                .anyMatch(value -> value.slot == TaskSlot.MIDDAY));
        assertEquals(ScheduleMoveResult.REJECTED_DUPLICATE_SLOT,
                new MoveScheduleEntry(repository, clock).execute(
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

        new MoveScheduleEntry(repository, clock).execute(
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
        new ToggleStep(repository, clock).execute(snapshot.id);

        new OrganizeTaskStep(repository).move(new StepMoveRequest(
                TaskStepId.of(moving.id), target.id, java.util.Optional.empty()));

        TaskStepTemplate moved = repository.templates(target.id).stream()
                .filter(value -> value.id.equals(moving.id)).findFirst().orElseThrow();
        assertNotNull(moved);
        assertEquals(target.id, repository.combo("step:" + moving.id).taskId);
        OccurrenceStep movedSnapshot = repository.findOccurrenceStep(snapshot.id);
        assertEquals(targetOccurrence.id, movedSnapshot.occurrenceId);
        assertTrue(movedSnapshot.done);
        assertTrue(repository.rewardBookings(targetOccurrence.id).stream()
                .anyMatch(value -> snapshot.id.equals(value.occurrenceStepId)));
    }

    @Test public void swappingStepsInsideOneTaskKeepsIdsAndReordersTheOpenSnapshot() {
        create("Routine", TaskSlot.MORNING, Recurrence.DAILY, Arrays.asList("Erster", "Zweiter"));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Task task = repository.allTasks().get(0);
        TaskStepTemplate first = repository.templates(task.id).get(0);
        TaskStepTemplate second = repository.templates(task.id).get(1);

        new OrganizeTaskStep(repository).swap(new StepSwapRequest(
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

        new OrganizeTaskStep(repository).move(new StepMoveRequest(
                TaskStepId.of(moving.id), target.id, java.util.Optional.empty()));

        assertTrue(repository.templates(source.id).isEmpty());
        assertEquals(moving.id, repository.templates(target.id).stream()
                .filter(value -> value.id.equals(moving.id)).findFirst().orElseThrow().id);
        assertEquals(sourceOccurrence.id,
                repository.findOccurrenceStep(snapshotId).occurrenceId);
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
        new CreateTask(repository, clock, ids, new TaskOrdering()).execute(title, slot,
                recurrence, 1, 0, steps, false, "");
    }

    private static final class SequenceIds implements IdGenerator {
        private int next;
        @Override public String nextId() { return "all-" + ++next; }
    }
}
