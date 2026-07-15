package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.UndoTaskCheckOffUseCase;
import com.autosecretary.features.task.application.internal.mutations.TaskSlotUndoMutation;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskCheckOffFixture;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TaskCompletionCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void twoPhaseCompletionKeepsStreakHistoryTimingAndAdaptivePrefSlotInvariant() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Adaptive Task", today);
        task.core.adaptive = true;
        task.core.progress.totalTime = 10;
        task.core.progress.totalProgress = 1;
        TaskPrefSlot prefSlot = TaskFixtures.prefSlot(task, today, LocalTime.of(10, 0));
        taskDao.write(task);

        CheckOffTaskUseCase useCase = createCheckOffUseCase();
        TaskListItem item = currentItem(task.core.id);

        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(item, startProbe.runnable());
        startProbe.assertCalled();

        Task afterStart = taskDao.read(task.core.id);
        TaskSlot startedSlot = afterStart.findSlot(item.slotId);
        assertNotNull(startedSlot.realStart);
        assertEquals(false, startedSlot.completed);
        assertEquals(0, afterStart.core.history.completions);

        startedSlot.realStart = LocalTime.now().minusMinutes(10);
        taskDao.writeSlot(startedSlot);

        CallbackProbe<Void> completeProbe = new CallbackProbe<>();
        useCase.execute(currentItem(task.core.id), completeProbe.runnable());
        completeProbe.assertCalled();

        Task completed = taskDao.read(task.core.id);
        TaskSlot completedSlot = completed.findSlot(item.slotId);
        assertTrue(completedSlot.completed);
        assertNotNull(completedSlot.realEnd);
        assertEquals(1, completed.core.history.completions);
        assertEquals(1, completed.core.history.trackedCompletions);
        assertEquals(1, completed.core.history.currentStreak);
        assertTrue(completed.core.history.totalDuration >= 10);
        assertTrue(completed.core.progress.totalProgress > 1);

        TaskPrefSlot adapted = completed.prefSlots.stream()
                .filter(slot -> slot.id.equals(prefSlot.id))
                .findFirst()
                .orElseThrow();
        assertNotEquals(LocalTime.of(10, 0), adapted.start);
    }

    /**
     * Invariant: a task without any slot (non-repeating tasks are never auto-scheduled) can be
     * checked off from the list — the first tap creates an ad-hoc unscheduled slot for today and
     * starts it, the second tap completes it and records history.
     */
    @Test
    public void slotlessTaskChecksOffViaAdHocSlotInvariant() {
        LocalDate today = LocalDate.now();
        Task oneOff = new Task();
        oneOff.core.title = "Einmalige Aufgabe";
        oneOff.core.created = today;
        taskDao.write(oneOff);

        CheckOffTaskUseCase useCase = createCheckOffUseCase();

        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(currentItem(oneOff.core.id), startProbe.runnable());
        startProbe.assertCalled();

        Task afterStart = taskDao.read(oneOff.core.id);
        assertEquals(1, afterStart.slots.size());
        TaskSlot adHoc = afterStart.slots.get(0);
        assertEquals(today, adHoc.day);
        assertEquals(false, adHoc.scheduled);
        assertNotNull(adHoc.realStart);
        assertEquals(false, adHoc.completed);

        CallbackProbe<Void> completeProbe = new CallbackProbe<>();
        useCase.execute(currentItem(oneOff.core.id), completeProbe.runnable());
        completeProbe.assertCalled();

        Task completed = taskDao.read(oneOff.core.id);
        assertTrue(completed.slots.get(0).completed);
        assertNotNull(completed.slots.get(0).realEnd);
        assertEquals(1, completed.core.history.completions);
    }

    /**
     * Invariant: undoing the STARTED phase of an ad-hoc check-off removes the ad-hoc slot
     * entirely instead of leaving an empty pending slot behind.
     */
    @Test
    public void undoingAdHocStartRemovesTheAdHocSlotInvariant() {
        LocalDate today = LocalDate.now();
        Task oneOff = new Task();
        oneOff.core.title = "Einmalige Aufgabe";
        oneOff.core.created = today;
        taskDao.write(oneOff);

        CheckOffTaskUseCase useCase = createCheckOffUseCase();
        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(currentItem(oneOff.core.id), startProbe.runnable());
        startProbe.assertCalled();

        SynchronousExecutorService executor = new SynchronousExecutorService();
        UndoTaskCheckOffUseCase undoUseCase = new UndoTaskCheckOffUseCase(
                new TaskSlotUndoMutation(taskDao, executor), executor);
        CallbackProbe<Void> undoProbe = new CallbackProbe<>();
        undoUseCase.execute(currentItem(oneOff.core.id), undoProbe.runnable());
        undoProbe.assertCalled();

        Task afterUndo = taskDao.read(oneOff.core.id);
        assertTrue("the ad-hoc slot is removed on undo", afterUndo.slots.isEmpty());
    }

    private CheckOffTaskUseCase createCheckOffUseCase() {
        return TaskCheckOffFixture.create(db);
    }

    private TaskListItem currentItem(String taskId) {
        return new TaskListItemMapper().map(List.of(taskDao.read(taskId))).get(0);
    }
}
