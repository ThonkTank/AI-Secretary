package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.CheckOffTaskUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.TaskCheckOffFixture;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class TaskLeisureCharacterizationTest extends AutoSecretaryRobolectricTest {
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

    /**
     * Invariant: completing a leisure task records the completion on the slot (it is still marked
     * done) but skips ALL performance metrics — no streak, no history counter, no adaptive pref-slot
     * shift. Leisure items are scheduled without carrying any performance pressure.
     */
    @Test
    public void leisureCompletionMarksDoneButSkipsStreakHistoryAndAdaptive() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Animal Crossing", today);
        task.core.leisure = true;
        task.core.adaptive = true;
        TaskPrefSlot prefSlot = TaskFixtures.prefSlot(task, today, LocalTime.of(10, 0));
        taskDao.write(task);

        CheckOffTaskUseCase useCase = TaskCheckOffFixture.create(db);
        TaskListItem item = currentItem(task.core.id);

        CallbackProbe<Void> startProbe = new CallbackProbe<>();
        useCase.execute(item, startProbe.runnable());
        startProbe.assertCalled();

        // Push realStart back so the second tap is not excluded as a sub-3s "quick tap".
        Task afterStart = taskDao.read(task.core.id);
        TaskSlot startedSlot = afterStart.findSlot(item.slotId);
        startedSlot.realStart = LocalTime.now().minusMinutes(10);
        taskDao.writeSlot(startedSlot);

        CallbackProbe<Void> completeProbe = new CallbackProbe<>();
        useCase.execute(currentItem(task.core.id), completeProbe.runnable());
        completeProbe.assertCalled();

        Task completed = taskDao.read(task.core.id);
        TaskSlot completedSlot = completed.findSlot(item.slotId);
        // Slot still marked done (two-phase check-off intact).
        assertTrue(completedSlot.completed);
        assertNotNull(completedSlot.realEnd);
        // But every performance metric is untouched.
        assertEquals(0, completed.core.history.completions);
        assertEquals(0, completed.core.history.currentStreak);
        TaskPrefSlot unchanged = completed.prefSlots.stream()
                .filter(slot -> slot.id.equals(prefSlot.id))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalTime.of(10, 0), unchanged.start);
    }

    /**
     * Invariant: a leisure task stays schedulable even with an expired deadline and
     * {@code closeOnMiss = true} — the deadline-expired hard gate that would exclude a normal task
     * is neutralised for leisure items, so they always get a slot.
     */
    @Test
    public void leisureTaskWithExpiredDeadlineStillGetsScheduled() {
        LocalDate today = LocalDate.now();
        Task task = flexibleTask("Entspannen", today);
        task.core.leisure = true;
        task.core.closeOnMiss = true;
        task.core.deadline = today.minusDays(1); // expired

        TaskSlotGenerator generator = windowGenerator(CategoryWindowProvider.NONE);
        generator.generateSlotsForWindow(List.of(task), today, 1, new TaskPlanningState());

        assertTrue("leisure task should be scheduled despite expired deadline", hasScheduledSlot(task));
    }

    private Task flexibleTask(String title, LocalDate day) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        task.core.minDuration = 30;
        task.core.maxDuration = 30;
        TaskFixtures.prefSlot(task, day, LocalTime.of(9, 0));
        return task;
    }

    private TaskSlotGenerator windowGenerator(CategoryWindowProvider categoryWindowProvider) {
        SchedulingWindowProvider windowProvider = day -> new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(day, LocalTime.of(9, 0)),
                LocalDateTime.of(day, LocalTime.of(11, 0)));
        return DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                windowProvider,
                CalendarBlockedIntervalProvider.NONE,
                categoryWindowProvider,
                () -> List.of(),
                candidate -> true);
    }

    private static boolean hasScheduledSlot(Task task) {
        return task.slots.stream().anyMatch(slot -> slot.scheduled);
    }

    private TaskListItem currentItem(String taskId) {
        return new TaskListItemMapper().map(List.of(taskDao.read(taskId))).get(0);
    }
}
