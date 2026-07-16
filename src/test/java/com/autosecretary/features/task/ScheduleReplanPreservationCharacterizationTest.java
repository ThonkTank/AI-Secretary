package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Invariant protected: a re-plan triggered through {@link ScheduleReplanCoordinator} never replaces
 * already-started or completed work — the demotivation guard the user asked for. Only untouched
 * scheduled slots may be regenerated.
 */
public final class ScheduleReplanPreservationCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private ScheduleReplanCoordinator coordinator;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        SynchronousExecutorService exec = new SynchronousExecutorService();
        TaskSlotGenerator generator = DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao()),
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                () -> List.of(),
                candidate -> true,
                () -> SchedulingTuning.NONE);
        RegenerateScheduleUseCase regenerate = new RegenerateScheduleUseCase(
                db, taskDao, generator, exec, exec, () -> true);
        coordinator = new ScheduleReplanCoordinator(regenerate, new WidgetRefreshNotifier() {
            @Override public void refreshTaskWidgets() { }
            @Override public void refreshBudgetWidgets() { }
        });
    }

    @After
    public void tearDown() {
        db.close();
    }

    /**
     * A slot the user has started (realStart set) survives a triggered re-plan unchanged: same id,
     * same realStart, not duplicated — the scheduler plans around it instead of replacing it.
     */
    @Test
    public void startedSlotSurvivesTriggeredReplanInvariant() {
        LocalDate today = LocalDate.now();
        Task task = schedulableTask("Gestartete Task", today, LocalTime.of(9, 0));
        taskDao.write(task);

        // First re-plan creates the scheduled slot.
        coordinator.requestReplan();
        Task afterFirst = taskDao.read(task.core.id);
        assertEquals(1, afterFirst.slots.size());
        TaskSlot planned = afterFirst.slots.get(0);
        assertTrue(planned.scheduled);

        // The user starts it.
        planned.realStart = LocalTime.of(9, 5);
        taskDao.writeSlot(planned);

        // A change elsewhere triggers another re-plan.
        coordinator.requestReplan();

        Task afterSecond = taskDao.read(task.core.id);
        List<TaskSlot> onDay = afterSecond.slots.stream()
                .filter(s -> today.equals(s.day))
                .toList();
        assertEquals("the started slot is preserved, not duplicated or replaced", 1, onDay.size());
        TaskSlot survivor = onDay.get(0);
        assertEquals(planned.id, survivor.id);
        assertNotNull("realStart is kept", survivor.realStart);
        assertEquals(LocalTime.of(9, 5), survivor.realStart);
    }

    /** A completed slot survives a triggered re-plan unchanged. */
    @Test
    public void completedSlotSurvivesTriggeredReplanInvariant() {
        LocalDate today = LocalDate.now();
        Task task = schedulableTask("Erledigte Task", today, LocalTime.of(10, 0));
        taskDao.write(task);

        coordinator.requestReplan();
        Task afterFirst = taskDao.read(task.core.id);
        TaskSlot planned = afterFirst.slots.get(0);
        planned.realStart = LocalTime.of(10, 2);
        planned.realEnd = LocalTime.of(10, 30);
        planned.completed = true;
        taskDao.writeSlot(planned);

        coordinator.requestReplan();

        Task afterSecond = taskDao.read(task.core.id);
        List<TaskSlot> onDay = afterSecond.slots.stream()
                .filter(s -> today.equals(s.day) && s.id.equals(planned.id))
                .toList();
        assertEquals(1, onDay.size());
        assertTrue("completion is kept", onDay.get(0).completed);
    }

    private static Task schedulableTask(String title, LocalDate today, LocalTime prefStart) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = today;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = today;
        task.core.minDuration = 30;
        task.core.maxDuration = 30;
        TaskFixtures.prefSlot(task, today, prefStart);
        return task;
    }
}
