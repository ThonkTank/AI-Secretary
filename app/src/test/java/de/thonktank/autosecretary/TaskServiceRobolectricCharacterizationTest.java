package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Arrays;

import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.TaskSlot;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskServiceRobolectricCharacterizationTest {
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

    @Test public void dashboardIsPureAndExplicitMaterializationCreatesAtMostOneOccurrence() {
        TaskEntity task = task("routine", "Morgenroutine", TaskSlot.MORNING.storageCode, "DAILY", 1_001_000L);
        dao.insertTask(task);
        dao.insertTemplates(Arrays.asList(
                new TaskStepEntity("one", task.id, 0, "Duschen"),
                new TaskStepEntity("two", task.id, 1, "Anziehen")));

        DashboardState before = service.dashboard();
        service.materializeDueTasks();
        DashboardState first = service.dashboard();
        service.materializeDueTasks();
        DashboardState second = service.dashboard();

        assertNull(before.firstOpen());
        assertEquals(1, dao.occurrencesByState(OccurrenceState.OPEN.storageCode()).size());
        assertEquals(2, first.firstOpen().steps.size());
        assertEquals(first.firstOpen().taskId, second.firstOpen().taskId);
    }

    @Test public void completionKeepsTheTaskVisibleTodayAndAwardsXpOnce() {
        TaskEntity task = task("once", "Brief beantworten", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        dao.insertTask(task);
        dao.insertOccurrence(new OccurrenceEntity("occurrence", task.id, TODAY.toString(), "OPEN", 1000, ""));

        service.complete("occurrence");
        service.complete("occurrence");
        DashboardState state = service.dashboard();

        assertEquals(10, state.xp);
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).done);
        assertTrue(dao.task(task.id).archived);
        assertNull(dao.openForTask(task.id, OccurrenceState.OPEN.storageCode()));
    }

    @Test public void deferSwapsWithTheNextOpenTask() {
        TaskEntity first = task("first", "Erste Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        TaskEntity second = task("second", "Zweite Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_002_000L);
        dao.insertTask(first);
        dao.insertTask(second);
        dao.insertOccurrence(new OccurrenceEntity("first-occurrence", first.id, TODAY.toString(), "OPEN", 1000, ""));
        dao.insertOccurrence(new OccurrenceEntity("second-occurrence", second.id, TODAY.toString(), "OPEN", 2000, ""));

        service.defer("first-occurrence");

        assertEquals("second", service.dashboard().firstOpen().taskId);
        assertEquals(2_048L, dao.task("first").displayOrder);
        assertEquals(1_024L, dao.task("second").displayOrder);
    }

    @Test public void ongoingTaskNeedsNoOccurrenceAndClosesOnlyThroughItsCondition() {
        TaskEntity task = task("ongoing", "Praktikum", TaskSlot.LATER.storageCode, "ONCE", 4_001_000L);
        task.ongoing = true;
        task.conditionText = "Vertrag unterschrieben";
        task.nextDueOn = "";
        dao.insertTask(task);

        assertNotNull(service.dashboard().firstOpen());
        service.closeOngoingTask(task.id);

        DashboardState state = service.dashboard();
        assertNull(state.firstOpen());
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).done);
        assertEquals(10, state.xp);
    }

    private static TaskEntity task(String id, String title, String slot, String recurrence, long order) {
        return new TaskEntity(id, title, slot, recurrence, 1, 0, false, "", false, false,
                TODAY.toString(), "", "", 1, 0, 0, "", order, false);
    }
}
