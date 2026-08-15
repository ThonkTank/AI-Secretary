package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.Arrays;

import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.TaskSlot;

@RunWith(AndroidJUnit4.class)
public final class TaskServiceCharacterizationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    private AppDatabase database;
    private TaskDao dao;
    private TaskService service;

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.tasks();
        service = new TaskService(database, () -> TODAY);
    }

    @After public void tearDown() {
        database.close();
    }

    @Test public void dashboardIsPureAndExplicitMaterializationCreatesOneOccurrence() {
        TaskEntity task = task("task", "Morgenroutine", TaskSlot.MORNING.storageCode, "DAILY", 1_001_000L);
        dao.insertTask(task);
        dao.insertTemplates(Arrays.asList(
                new TaskStepEntity("template-1", task.id, 0, "Duschen"),
                new TaskStepEntity("template-2", task.id, 1, "Anziehen")));

        DashboardState before = service.dashboard();
        service.materializeDueTasks();
        DashboardState state = service.dashboard();

        assertNull(before.firstOpen());
        assertEquals(1, dao.occurrencesByState(OccurrenceState.OPEN.storageCode()).size());
        assertEquals(1, state.tasks.size());
        assertEquals(2, state.tasks.get(0).steps.size());
        service.materializeDueTasks();
        assertEquals("Repeated reads must not stack occurrences", 1,
                dao.occurrencesByState(OccurrenceState.OPEN.storageCode()).size());
    }

    @Test public void completedOneOffTaskRemainsVisibleForTheCurrentDay() {
        TaskEntity task = task("once", "Brief beantworten", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        dao.insertTask(task);
        OccurrenceEntity occurrence = new OccurrenceEntity(
                "occurrence", task.id, TODAY.toString(), "OPEN", 1000, "");
        dao.insertOccurrence(occurrence);

        service.complete(occurrence.id);
        DashboardState state = service.dashboard();

        assertEquals(10, state.xp);
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).done);
        assertTrue(dao.task(task.id).archived);
        assertNull(dao.openForTask(task.id, OccurrenceState.OPEN.storageCode()));
    }

    @Test public void deferSwapsOnlyTheSelectedTaskWithTheNextOpenTask() {
        TaskEntity first = task("first", "Erste Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        TaskEntity second = task("second", "Zweite Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_002_000L);
        dao.insertTask(first);
        dao.insertTask(second);
        dao.insertOccurrence(new OccurrenceEntity("first-occurrence", first.id, TODAY.toString(), "OPEN", 1000, ""));
        dao.insertOccurrence(new OccurrenceEntity("second-occurrence", second.id, TODAY.toString(), "OPEN", 2000, ""));

        service.defer("first-occurrence");

        DashboardState state = service.dashboard();
        assertEquals("second", state.firstOpen().taskId);
        assertEquals(2_048L, dao.task("first").displayOrder);
        assertEquals(1_024L, dao.task("second").displayOrder);
    }

    @Test public void ongoingTaskWithoutOccurrenceStillAppearsUntilItsConditionIsClosed() {
        TaskEntity task = task("ongoing", "Praktikum", TaskSlot.LATER.storageCode, "ONCE", 4_001_000L);
        task.ongoing = true;
        task.conditionText = "Vertrag unterschrieben";
        task.nextDueOn = "";
        dao.insertTask(task);

        DashboardState before = service.dashboard();
        assertNotNull(before.firstOpen());
        assertTrue(before.firstOpen().terminalCondition);

        service.closeOngoingTask(task.id);

        DashboardState after = service.dashboard();
        assertNull(after.firstOpen());
        assertEquals(1, after.tasks.size());
        assertTrue(after.tasks.get(0).done);
        assertEquals(10, after.xp);
    }

    @Test public void sameWeekCompletionsCountOnlyOneRingWeekAndLateCompletionRestartsIt() {
        TaskEntity task = task("routine", "Routine", TaskSlot.MORNING.storageCode, "DAILY", 1_001_000L);
        dao.insertTask(task);
        dao.insertOccurrence(new OccurrenceEntity("day-one", task.id, TODAY.toString(), "OPEN", 1000, ""));

        service.complete("day-one");
        assertEquals(1, dao.task(task.id).routineStreakWeeks);

        TaskEntity afterFirst = dao.task(task.id);
        dao.insertOccurrence(new OccurrenceEntity("day-two", task.id, TODAY.plusDays(1).toString(), "OPEN", 1000, ""));
        TaskService sundayService = new TaskService(database, () -> TODAY.plusDays(1));
        sundayService.complete("day-two");
        assertEquals(1, dao.task(task.id).routineStreakWeeks);

        dao.insertOccurrence(new OccurrenceEntity("late", task.id, TODAY.plusDays(2).toString(), "OPEN", 1000, ""));
        TaskService lateService = new TaskService(database, () -> TODAY.plusDays(3));
        lateService.complete("late");
        assertEquals(0, dao.task(task.id).routineStreakWeeks);
        assertTrue(dao.task(task.id).routineLevel >= afterFirst.routineLevel);
        assertFalse(dao.occurrencesByState(OccurrenceState.OPEN.storageCode()).stream()
                .anyMatch(item -> "late".equals(item.id)));
    }

    private static TaskEntity task(String id, String title, String slot, String recurrence, long order) {
        return new TaskEntity(id, title, slot, recurrence, 1, 0, false, "", false, false,
                TODAY.toString(), "", "", 1, 0, 0, "", order, false);
    }
}
