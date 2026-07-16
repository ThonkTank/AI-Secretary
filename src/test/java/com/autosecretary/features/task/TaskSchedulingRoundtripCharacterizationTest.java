package com.autosecretary.features.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.TaskBudgetEligibilityService;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TaskFixtures;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

public final class TaskSchedulingRoundtripCharacterizationTest extends AutoSecretaryRobolectricTest {
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
    public void scheduleGenerationPersistsGeneratedSlotsWithStableCoreFieldsInvariant() {
        LocalDate today = LocalDate.now();
        Task task = new Task();
        task.core.title = "Planbare Task";
        task.core.created = today;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = com.autosecretary.shared.Period.DAY;
        task.core.repetition.periodStart = today;
        task.core.minDuration = 30;
        task.core.maxDuration = 30;
        TaskFixtures.prefSlot(task, today, LocalTime.of(9, 0));
        taskDao.write(task);

        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskSlotGenerator generator = DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao()),
                CalendarBlockedIntervalProvider.NONE,
                com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider.NONE,
                () -> java.util.List.of(),
                taskCandidate -> true,
                () -> com.autosecretary.features.task.domain.scheduling.SchedulingTuning.NONE);
        RegenerateScheduleUseCase useCase = new RegenerateScheduleUseCase(
                db,
                taskDao,
                generator,
                executor,
                executor,
                () -> true);

        CallbackProbe<RegenerateScheduleUseCase.Result> probe = new CallbackProbe<>();
        useCase.execute(probe.consumer());
        RegenerateScheduleUseCase.Result result = probe.value();

        assertTrue(result.createdSlots() > 0);
        Task persisted = taskDao.read(task.core.id);
        assertFalse(persisted.slots.isEmpty());
        TaskSlot slot = persisted.slots.get(0);
        assertTrue(task.core.id.equals(slot.taskId));
        assertTrue(!slot.day.isBefore(today) && slot.day.isBefore(today.plusDays(7)));
        assertNotNull(slot.start);
        assertNotNull(slot.end);
        assertTrue(slot.end.minusMinutes(30).equals(slot.start));
        assertTrue(slot.scheduled);
    }
}
