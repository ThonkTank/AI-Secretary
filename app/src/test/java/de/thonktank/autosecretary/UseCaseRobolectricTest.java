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
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskOrdering;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.repository.ApplicationTaskRepository;
import de.thonktank.autosecretary.domain.usecase.CloseOngoingTask;
import de.thonktank.autosecretary.domain.usecase.CompleteOccurrence;
import de.thonktank.autosecretary.domain.usecase.CreateTask;
import de.thonktank.autosecretary.domain.usecase.DeferTask;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.usecase.IdGenerator;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.LoadTaskCatalog;
import de.thonktank.autosecretary.domain.usecase.LoadTaskDetails;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.schedule.MoveTaskPlacement;
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
    private ApplicationTaskRepository repository;
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
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock, ids);
        LoadDashboard load = new LoadDashboard(repository);
        create.execute(TaskDefinition.basic("Morgenroutine", TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0, Arrays.asList("Duschen", "Anziehen")));

        assertTrue(load.execute(TODAY).tasks.isEmpty());
        assertTrue(repository.openOccurrences().isEmpty());

        materialize.execute();
        materialize.execute();
        Dashboard dashboard = load.execute(TODAY);

        assertEquals(1, repository.openOccurrences().size());
        assertEquals(1, dashboard.tasks.size());
        assertEquals(2, dashboard.tasks.get(0).steps.size());
        assertEquals(1_024L, dashboard.tasks.get(0).task.catalogOrder);
    }

    @Test public void editorCatalogAndTodayReadTheSameCanonicalSchedule() {
        TaskDefinition definition = new TaskDefinition("Mehrfach", 20, TaskSlot.MORNING,
                Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit | TimeOfDay.EVENING.bit,
                TaskBoundKind.FOREVER, null, null, null, null, "", Collections.emptyList());
        new CreateTask(repository, repository, clock, ids).execute(definition);
        Task task = repository.allTasks().get(0);

        assertEquals(Arrays.asList(TaskSlot.MORNING, TaskSlot.EVENING),
                new TaskSchedule(repository.scheduleEntries(task.id)).slots(task.id).stream()
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(TimeOfDay.MORNING.bit | TimeOfDay.EVENING.bit,
                new LoadTaskDetails(repository).execute(task.id).timeOfDayMask);
        TaskCatalog.Item catalog = new LoadTaskCatalog(repository).execute().items.get(0);
        assertEquals(Arrays.asList(TaskSlot.MORNING, TaskSlot.EVENING), catalog.schedule.stream()
                .map(value -> value.slot).collect(java.util.stream.Collectors.toList()));

        new MaterializeDueOccurrences(repository, clock, ids).execute();
        assertEquals(Arrays.asList(TaskSlot.MORNING, TaskSlot.EVENING),
                new LoadDashboard(repository).execute(TODAY).tasks.stream()
                        .map(value -> value.displaySlot)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Test public void transactionPortReturnsItsResult() {
        assertEquals("committed", repository.inTransaction(() -> "committed"));
    }

    @Test public void dashboardUsesAConstantNumberOfBulkSelects() {
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        for (int i = 0; i < 6; i++)
            create.execute(TaskDefinition.basic("Aufgabe " + i, TaskSlot.MORNING,
                    Recurrence.DAILY, 1, 0, Arrays.asList("Schritt A", "Schritt B")));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        LoadDashboard load = new LoadDashboard(repository);

        queries.clear();
        Dashboard dashboard = load.execute(TODAY);

        assertEquals(6, dashboard.tasks.size());
        assertTrue("Dashboard query count was " + queries.size(), queries.size() <= 9);
        assertEquals(1, queries.stream().filter(sql -> sql.contains("occurrence_steps")).count());
        assertEquals(1, queries.stream().filter(sql -> sql.contains("repetition_results")).count());
    }

    @Test public void materializationLoadsOpenTasksAndTemplatesInBulk() {
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        for (int i = 0; i < 6; i++)
            create.execute(TaskDefinition.basic("Aufgabe " + i, TaskSlot.MORNING,
                    Recurrence.DAILY, 1, 0, Arrays.asList("Schritt A", "Schritt B")));
        MaterializeDueOccurrences materialize = new MaterializeDueOccurrences(repository, clock, ids);

        queries.clear();
        materialize.execute();

        long materializationQueries = queries.size();
        assertTrue("Materialization query count was " + materializationQueries,
                materializationQueries <= 10);
        assertEquals(1, queries.stream().filter(sql -> sql.contains("task_steps")).count());
        assertEquals(6, repository.openOccurrences().size());
    }

    @Test public void updateMoveAndDeleteAreIndependentCommands() {
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        create.execute(TaskDefinition.basic("Erste", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.emptyList()));
        create.execute(TaskDefinition.basic("Zweite", TaskSlot.EVENING,
                Recurrence.ONCE, 1, 0, Collections.emptyList()));
        List<Task> tasks = ordering.sorted(repository.allTasks());
        TaskId first = tasks.get(0).id;
        TaskId second = tasks.get(1).id;
        UpdateTask update = new UpdateTask(repository, repository, ids, clock);

        update.execute(first, new TaskDefinition("Umbenannt", null, TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, 0, TaskBoundKind.FOREVER, null, null, null,
                null, "", Collections.emptyList()));
        new MoveTaskPlacement(repository).execute(first, null, TaskSlot.EVENING);
        new DeleteTask(repository).execute(second);

        assertEquals(1, repository.allTasks().size());
        assertEquals("Umbenannt", repository.findTask(first).title);
        assertEquals(TaskSlot.EVENING, repository.scheduleEntries(first).get(0).slot);
        assertNull(repository.findTask(second));
    }

    @Test public void updateCanReplaceTheCompleteEditableDefinition() {
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        create.execute(TaskDefinition.basic("Alt", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.singletonList("Alter Schritt")));
        Task task = repository.allTasks().get(0);

        List<TaskStepDefinition> replacements = Arrays.asList(
                new TaskStepDefinition(null, 0, "Erster Schritt", 0,
                        StepAmount.none(), ""),
                new TaskStepDefinition(null, 1, "Zweiter Schritt", 0,
                        StepAmount.none(), ""));
        new UpdateTask(repository, repository, ids, clock).execute(task.id,
                new TaskDefinition("Neu", 25, TaskSlot.EVENING,
                        Recurrence.WEEKDAYS, 1, 1 << 0 | 1 << 4,
                        TimeOfDay.EVENING.bit, TaskBoundKind.FOREVER, null, null, null,
                        null, "Notiz", replacements));

        Task updated = repository.findTask(task.id);
        assertEquals("Neu", updated.title);
        assertEquals(TaskSlot.EVENING, repository.scheduleEntries(task.id).get(0).slot);
        assertEquals(Recurrence.WEEKDAYS, updated.recurrence);
        assertEquals(1, updated.intervalDays);
        assertEquals(17, updated.weekdayMask);
        assertEquals(Integer.valueOf(25), updated.estimatedMinutes);
        assertEquals("Notiz", updated.note);
        assertEquals(2, repository.templates(task.id).size());
        assertEquals("Erster Schritt", repository.templates(task.id).get(0).text);
    }

    @Test public void editingArchivedDefinitionKeepsItArchived() {
        new CreateTask(repository, repository, clock, ids).execute(TaskDefinition.basic(
                "Archiv", TaskSlot.MORNING, Recurrence.ONCE, 1, 0,
                Collections.singletonList("Alt")));
        Task task = repository.allTasks().get(0);
        repository.updateTask(task.withOccurrenceState(true, task.nextDueOn,
                task.lastScheduledOn, TODAY, task.hasCompletedOccurrence));

        new UpdateTask(repository, repository, ids, clock).execute(task.id,
                TaskDefinition.basic("Archiv geändert", TaskSlot.EVENING,
                        Recurrence.ONCE, 1, 0, Collections.singletonList("Neu")));

        Task updated = repository.findTask(task.id);
        assertTrue(updated.archived);
        assertEquals("Archiv geändert", updated.title);
        assertEquals(TaskSlot.EVENING, repository.scheduleEntries(task.id).get(0).slot);
        assertEquals("Neu", repository.templates(task.id).get(0).text);
    }

    @Test public void toggleAndCompleteAreIdempotentCommandsWithIsolatedXpPolicy() {
        new CreateTask(repository, repository, clock, ids).execute(TaskDefinition.basic(
                "Routine", TaskSlot.MORNING, Recurrence.DAILY, 1, 0,
                Collections.singletonList("Schritt")));
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
        CreateTask create = new CreateTask(repository, repository, clock, ids);
        create.execute(TaskDefinition.basic("Erste", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.emptyList()));
        create.execute(TaskDefinition.basic("Zweite", TaskSlot.MORNING,
                Recurrence.ONCE, 1, 0, Collections.emptyList()));
        new MaterializeDueOccurrences(repository, clock, ids).execute();
        LoadDashboard load = new LoadDashboard(repository);
        Dashboard before = load.execute(TODAY);
        TaskId first = before.tasks.get(0).task.id;
        String occurrenceId = before.tasks.get(0).occurrence.id;

        new DeferTask(repository, repository).execute(occurrenceId);
        Dashboard after = load.execute(TODAY);

        assertNotEquals(first, after.tasks.get(0).task.id);
        assertEquals(first, after.tasks.get(1).task.id);
    }

    @Test public void closingOngoingTaskCreatesAReceiptAndCanBeFullyUndoneToday() {
        Task ongoing = Task.restore(TaskId.of("ongoing"), "Praktikum", Recurrence.ONCE,
                1, 0, true, "Vertrag unterschrieben", false, false, null, null, null,
                1_024L, false, null, TaskBoundKind.FOREVER, null, null, null, null, "");
        repository.insertTask(ongoing);
        repository.putScheduleEntries(Collections.singletonList(
                new de.thonktank.autosecretary.domain.model.TaskScheduleEntry(
                        "ongoing-schedule", ongoing.id, TaskSlot.LATER, 1_024L)));
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
        Task task = Task.restore(TaskId.of("multi"), "Mehrfach", Recurrence.DAILY,
                1, 0, false, "", false, false, TODAY.plusDays(1), null, null,
                1_024L, false, null, TaskBoundKind.FOREVER, null, null, null, null, "");
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
        assertEquals(TODAY.plusDays(1), repository.findTask(task.id).nextDueOn);
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
