package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TaskRepository;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.DeferTask;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.MoveTask;
import de.thonktank.autosecretary.domain.usecase.ToggleStep;
import de.thonktank.autosecretary.domain.usecase.UndoOccurrence;
import de.thonktank.autosecretary.domain.usecase.UpdateTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class UseCaseRobolectricTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    private final List<String> queries = new CopyOnWriteArrayList<>();
    private AppDatabase database;
    private TaskRepository repository;
    private SequenceIds ids;
    private Clock clock;
    private TaskOrdering ordering;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryCallback((sql, arguments) -> {
                    if (sql.trim().toUpperCase(java.util.Locale.ROOT).startsWith("SELECT")
                            && !sql.contains("room_table_modification_log"))
                        queries.add(sql);
                }, Runnable::run)
                .build();
        repository = new RoomTaskRepository(database);
        ids = new SequenceIds();
        clock = new Clock() {
            @Override public LocalDate today() { return TODAY; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
        ordering = new TaskOrdering();
    }

    @After public void tearDown() {
        database.close();
    }

    @Test public void createMaterializeAndLoadAreSeparateAndIdempotent() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock, ids);
        LoadDashboard load = new LoadDashboard(repository);
        create.execute("Morgenroutine", TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Arrays.asList("Duschen", "Anziehen"), false, "");

        assertTrue(load.execute(TODAY).tasks.isEmpty());
        assertTrue(repository.openOccurrences().isEmpty());

        materialize.execute();
        materialize.execute();
        Dashboard dashboard = load.execute(TODAY);

        assertEquals(1, repository.openOccurrences().size());
        assertEquals(1, dashboard.tasks.size());
        assertEquals(2, dashboard.tasks.get(0).steps.size());
        assertEquals(1_024L, dashboard.tasks.get(0).task.displayOrder);
    }

    @Test public void transactionPortReturnsItsResult() {
        assertEquals("committed", repository.inTransaction(() -> "committed"));
    }

    @Test public void dashboardUsesAConstantNumberOfBulkSelects() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        for (int i = 0; i < 6; i++)
            create.execute("Aufgabe " + i, TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                    Arrays.asList("Schritt A", "Schritt B"), false, "");
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        LoadDashboard load = new LoadDashboard(repository);

        queries.clear();
        Dashboard dashboard = load.execute(TODAY);

        assertEquals(6, dashboard.tasks.size());
        assertTrue("Dashboard query count was " + queries.size(), queries.size() <= 8);
        assertEquals(1, queries.stream().filter(sql -> sql.contains("occurrence_steps")).count());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("repetition_results")).count());
    }

    @Test public void materializationLoadsOpenTasksAndTemplatesInBulk() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        for (int i = 0; i < 6; i++)
            create.execute("Aufgabe " + i, TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                    Arrays.asList("Schritt A", "Schritt B"), false, "");
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock, ids);

        queries.clear();
        materialize.execute();

        long materializationQueries = queries.size();
        assertTrue("Materialization query count was " + materializationQueries,
                materializationQueries <= 3);
        assertEquals(1, queries.stream().filter(sql -> sql.contains("task_steps")).count());
        assertEquals(6, repository.openOccurrences().size());
    }

    @Test public void updateMoveAndDeleteAreIndependentCommands() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        create.execute("Erste", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");
        create.execute("Zweite", TaskSlot.EVENING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");
        List<Task> tasks = ordering.sorted(repository.allTasks());
        TaskId first = tasks.get(0).id;
        TaskId second = tasks.get(1).id;
        UpdateTask update = new UpdateTask(repository, ordering);

        update.execute(first, "Umbenannt", TaskSlot.MORNING);
        new MoveTask(repository, ordering).execute(first, TaskSlot.EVENING);
        new DeleteTask(repository).execute(second);

        assertEquals(1, repository.allTasks().size());
        assertEquals("Umbenannt", repository.findTask(first).title);
        assertEquals(TaskSlot.EVENING, repository.findTask(first).slot);
        assertNull(repository.findTask(second));
    }

    @Test public void updateCanReplaceTheCompleteEditableDefinition() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        create.execute("Alt", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.singletonList("Alter Schritt"), false, "");
        Task task = repository.allTasks().get(0);

        new UpdateTask(repository, ordering, ids).execute(task.id, "Neu", TaskSlot.EVENING,
                Recurrence.WEEKDAYS, 3, 1 << 0 | 1 << 4,
                Arrays.asList("Erster Schritt", "Zweiter Schritt"), true, "Ziel erreicht");

        Task updated = repository.findTask(task.id);
        assertEquals("Neu", updated.title);
        assertEquals(TaskSlot.EVENING, updated.slot);
        assertEquals(Recurrence.WEEKDAYS, updated.recurrence);
        assertEquals(3, updated.intervalDays);
        assertEquals(17, updated.weekdayMask);
        assertTrue(updated.ongoing);
        assertEquals("Ziel erreicht", updated.conditionText);
        assertEquals(2, repository.templates(task.id).size());
        assertEquals("Erster Schritt", repository.templates(task.id).get(0).text);
    }

    @Test public void toggleAndCompleteAreIdempotentCommandsWithIsolatedXpPolicy() {
        new CreateTask(repository, clock, ids, ordering).execute(
                "Routine", TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Collections.singletonList("Schritt"), false, "");
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        Occurrence occurrence = repository.openOccurrences().get(0);
        String stepId = repository.occurrenceSteps(occurrence.id).get(0).id;

        new ToggleStep(repository, clock).execute(stepId);
        assertTrue(repository.findOccurrenceStep(stepId).done);

        CompleteOccurrence complete = new CompleteOccurrence(repository, clock);
        complete.execute(occurrence.id);
        complete.execute(occurrence.id);

        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(occurrence.id).state);
        assertEquals(10, repository.xp());
        assertEquals(3, repository.combo("task:" + occurrence.taskId.value).points);
        assertEquals(1, repository.combo(repository.findOccurrenceStep(stepId).comboOwnerId).points);
    }

    @Test public void deferSwapsOnlyTheSelectedAndNextOpenTask() {
        CreateTask create = new CreateTask(repository, clock, ids, ordering);
        create.execute("Erste", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");
        create.execute("Zweite", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.emptyList(), false, "");
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        LoadDashboard load = new LoadDashboard(repository);
        Dashboard before = load.execute(TODAY);
        TaskId first = before.tasks.get(0).task.id;
        String occurrenceId = before.tasks.get(0).occurrence.id;

        new DeferTask(repository, load, ordering, clock).execute(occurrenceId);
        Dashboard after = load.execute(TODAY);

        assertNotEquals(first, after.tasks.get(0).task.id);
        assertEquals(first, after.tasks.get(1).task.id);
    }

    @Test public void closingOngoingTaskCreatesAReceiptAndCanBeFullyUndoneToday() {
        Task ongoing = Task.create(TaskId.of("ongoing"), "Praktikum", TaskSlot.LATER,
                Recurrence.ONCE, 1, 0, true, "Vertrag unterschrieben", null, 1_024L);
        repository.insertTask(ongoing);
        CloseOngoingTask close = new CloseOngoingTask(repository, clock);

        close.execute(ongoing.id);
        close.execute(ongoing.id);

        assertTrue(repository.findTask(ongoing.id).archived);
        assertTrue(repository.findTask(ongoing.id).conditionDone);
        assertEquals(10, repository.xp());

        Occurrence completion = repository.occurrences(ongoing.id).get(0);
        assertEquals(OccurrenceKind.CONDITION, completion.kind);
        assertEquals(RewardBooking.Kind.CONDITION_COMPLETION,
                repository.rewardBookings(completion.id).get(0).kind);
        assertEquals(10, repository.rewardBookings(completion.id).stream()
                .filter(value -> value.target
                        == de.thonktank.autosecretary.domain.model.RewardBooking.Target.HEAD)
                .mapToInt(value -> value.xpDelta).sum());
        new UndoOccurrence(repository, clock).execute(completion.id);

        assertFalse(repository.findTask(ongoing.id).archived);
        assertFalse(repository.findTask(ongoing.id).conditionDone);
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(completion.id).state);
        assertEquals(0, repository.xp());
    }

    @Test public void arbitraryTodayUndoUsesTargetedScheduleProjectionAndExactBooking() {
        Task task = Task.create(TaskId.of("multi"), "Mehrfach", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, false, "", TODAY.minusDays(1), 1_024L);
        repository.insertTask(task);
        Occurrence older = new Occurrence("older", task.id, TODAY.minusDays(1),
                TaskSlot.MORNING, OccurrenceState.OPEN, 1, null);
        Occurrence newer = new Occurrence("newer", task.id, TODAY,
                TaskSlot.MORNING, OccurrenceState.OPEN, 2, null);
        repository.insertOccurrence(older); repository.insertOccurrence(newer);
        CompleteOccurrence complete = new CompleteOccurrence(repository, clock);
        complete.execute(older.id); complete.execute(newer.id);
        assertEquals(25, repository.xp());
        assertEquals(TODAY.plusDays(1), repository.findTask(task.id).nextDueOn);

        queries.clear();
        RewardReceipt undo = new UndoOccurrence(repository, clock).execute(older.id);

        assertEquals(-15, undo.xp);
        assertEquals(10, repository.xp());
        assertEquals(OccurrenceState.OPEN, repository.findOccurrence(older.id).state);
        assertEquals(OccurrenceState.COMPLETED, repository.findOccurrence(newer.id).state);
        assertEquals(TODAY.minusDays(1), repository.findTask(task.id).nextDueOn);
        assertEquals(0, queries.stream().filter(sql -> sql.matches(
                "(?s).*FROM occurrences WHERE taskId = \\?.*") && !sql.contains("LIMIT 1")).count());
        assertTrue(queries.stream().filter(sql -> sql.contains("FROM occurrences")
                && sql.contains("LIMIT 1")).count() >= 2);
    }

    private static final class SequenceIds implements IdGenerator {
        private int value;
        @Override public String nextId() { return "id-" + ++value; }
    }
}
