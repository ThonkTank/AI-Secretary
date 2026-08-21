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
import java.time.LocalTime;
import java.util.Arrays;

import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.presentation.DashboardUiMapper;

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
        service = service(TODAY);
    }

    @After public void tearDown() {
        database.close();
    }

    @Test public void dashboardIsPureAndExplicitMaterializationCreatesAtMostOneOccurrence() {
        TaskEntity task = task("routine", "Morgenroutine", TaskSlot.MORNING.storageCode, "DAILY", 1_001_000L);
        dao.insertTemplates(Arrays.asList(
                new TaskStepEntity("one", task.id, 0, "Duschen"),
                new TaskStepEntity("two", task.id, 1, "Anziehen")));

        TodayUiModel before = service.dashboard();
        service.materializeDueTasks();
        TodayUiModel first = service.dashboard();
        service.materializeDueTasks();
        TodayUiModel second = service.dashboard();

        assertNull(before.firstOpen());
        assertEquals(1, dao.occurrencesByState(OccurrenceState.OPEN.storageCode()).size());
        assertEquals(2, first.firstOpen().steps.size());
        assertEquals(first.firstOpen().taskId, second.firstOpen().taskId);
    }

    @Test public void completionKeepsTheTaskVisibleTodayAndAwardsXpOnce() {
        TaskEntity task = task("once", "Brief beantworten", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        dao.insertOccurrence(new OccurrenceEntity("occurrence", task.id, TODAY.toString(), "OPEN", 1000, ""));

        service.complete("occurrence");
        service.complete("occurrence");
        TodayUiModel state = service.dashboard();

        assertEquals(10, state.xp);
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).done);
        assertTrue(dao.task(task.id).archived);
        assertNull(dao.openForTask(task.id, OccurrenceState.OPEN.storageCode()));
    }

    @Test public void deferSwapsWithTheNextOpenTask() {
        TaskEntity first = task("first", "Erste Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_001_000L);
        TaskEntity second = task("second", "Zweite Aufgabe", TaskSlot.MORNING.storageCode, "ONCE", 1_002_000L);
        dao.insertOccurrence(new OccurrenceEntity("first-occurrence", first.id, TODAY.toString(), "OPEN", 1000, ""));
        dao.insertOccurrence(new OccurrenceEntity("second-occurrence", second.id, TODAY.toString(), "OPEN", 2000, ""));

        service.defer("first-occurrence");

        assertEquals("second", service.dashboard().firstOpen().taskId);
        assertEquals(1_001_000L, dao.task("first").catalogOrder);
        assertEquals(1_002_000L, dao.task("second").catalogOrder);
        assertTrue(dao.occurrence("first-occurrence").sortOrder
                > dao.occurrence("second-occurrence").sortOrder);
    }

    @Test public void ongoingTaskNeedsNoOccurrenceAndClosesOnlyThroughItsCondition() {
        TaskEntity task = task("ongoing", "Praktikum", TaskSlot.LATER.storageCode, "ONCE", 4_001_000L);
        task.ongoing = true;
        task.conditionText = "Vertrag unterschrieben";
        task.nextDueOn = "";
        dao.updateTask(task);

        assertNotNull(service.dashboard().firstOpen());
        service.closeOngoingTask(task.id);

        TodayUiModel state = service.dashboard();
        assertNull(state.firstOpen());
        assertEquals(1, state.tasks.size());
        assertTrue(state.tasks.get(0).done);
        assertEquals(10, state.xp);
    }

    private TaskEntity task(String id, String title, String slot, String recurrence, long order) {
        TaskEntity task = new TaskEntity(id, title, recurrence, 1, 0, false, "", false, false,
                TODAY.toString(), "", "", order, false, null,
                "FOREVER", "", null, null, "", "");
        dao.insertTask(task);
        dao.putScheduleEntries(java.util.Collections.singletonList(new TaskScheduleEntity(
                "schedule:" + id, id, slot, order)));
        return task;
    }

    private static Clock clock(LocalDate day) {
        return new Clock() {
            @Override public LocalDate today() { return day; }
            @Override public LocalTime time() { return LocalTime.NOON; }
        };
    }

    private TaskService service(LocalDate day) {
        Context context = ApplicationProvider.getApplicationContext();
        return new TaskService(database, clock(day),
                new DashboardUiMapper(new AndroidUiTextProvider(context)));
    }
}
