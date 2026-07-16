package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.ApplyTaskChangesUseCase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.TaskChangeUndoHolder;
import com.autosecretary.features.task.application.config.TaskCategoryWindowRepository;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
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
import java.util.List;

/**
 * Regression tests for the "empty day plan" incident: an assistant-created one-off task persisted a
 * null {@code periodUnit}, which NPE'd schedule generation; the caught exception then returned an
 * empty result <em>after</em> the plan's slots had already been deleted, wiping the day.
 *
 * <p>These would have caught it: they drive the <em>real</em> generator over a null-period task and
 * assert the plan is still produced, and they assert a failed run never destroys the existing plan.
 */
public final class ScheduleGenerationHardeningCharacterizationTest extends AutoSecretaryRobolectricTest {
    private AppDatabase db;
    private TaskDao taskDao;
    private SynchronousExecutorService exec;

    @Before
    public void setUp() {
        db = TestDatabases.inMemory();
        taskDao = db.taskDao();
        exec = new SynchronousExecutorService();
    }

    @After
    public void tearDown() {
        db.close();
    }

    /**
     * The core regression: a task with a null {@code periodUnit} (as legacy assistant one-off data
     * has it) must not throw inside the generator. Before the fix, {@code periodInDays()} NPE'd here,
     * which {@code RegenerateScheduleUseCase} then swallowed into an empty result — wiping the plan.
     * Driven at the generator level so the crash is observable (the use case would hide it).
     */
    @Test
    public void nullPeriodUnitTaskDoesNotCrashGenerationInvariant() {
        LocalDate today = LocalDate.now();
        Task broken = schedulableTask("Kaputte Task", today, LocalTime.of(9, 0));
        broken.core.repetition.periodUnit = null;   // the exact state that used to NPE the scheduler
        broken.core.repetition.reps = 1;            // reps>0 forces the scorer to compute repsPerDay()
        taskDao.write(broken);

        TaskSlotGenerator generator = realGenerator();
        List<Task> tasks = taskDao.readAll();
        TaskPlanningState state = new TaskPlanningState();
        generator.recordPreservedSlots(tasks, today, today.plusDays(7), state);

        // Must complete without throwing; the malformed task is simply non-schedulable.
        TaskSlotGenerationResult result = generator.generateSlotsForWindow(tasks, today, 7, state);
        assertNotNull(result);
        assertEquals("a null-period task is non-schedulable", 0,
                taskDao.read(broken.core.id).core.repetition.repsPerDay());
    }

    /** Sanity: a well-formed task schedules through the real generator + use case (harness works). */
    @Test
    public void validTaskSchedulesInvariant() {
        LocalDate today = LocalDate.now();
        taskDao.write(schedulableTask("Gültige Task", today, LocalTime.of(9, 0)));

        CallbackProbe<RegenerateScheduleUseCase.Result> probe = new CallbackProbe<>();
        realRegenerate().execute(probe.consumer());

        assertTrue(probe.value().createdSlots() > 0);
    }

    /** An assistant CREATE without a repetition block persists a valid (non-null) period unit. */
    @Test
    public void assistantCreateWithoutRepetitionPersistsValidPeriodUnitInvariant() {
        ApplyTaskChangesUseCase applyUseCase = new ApplyTaskChangesUseCase(
                db, taskDao, db.taskCategoryDao(), db.taskCategoryWindowDao(),
                new TaskCategoryWindowRepository(db.taskCategoryWindowDao(), db.taskCategoryDao()),
                new TaskChangeUndoHolder(),
                com.autosecretary.testing.ReplanCoordinators.inert(), exec, exec);

        // Mirrors the assistant's one-off task: CREATE with no RepetitionChange.
        applyUseCase.apply(new TaskAssistantProposal(List.of(), List.of(
                new TaskChange(ChangeOp.CREATE, null, "Einmalige Assistenten-Task",
                        null, null, null, null))), () -> {}, error -> {});

        List<Task> tasks = taskDao.readAll();
        assertEquals(1, tasks.size());
        assertNotNull("periodUnit must never persist as null", tasks.get(0).core.repetition.periodUnit);
    }

    /** A generation failure rolls back: the existing regeneratable plan survives instead of being wiped. */
    @Test
    public void failedGenerationPreservesExistingPlanInvariant() {
        LocalDate today = LocalDate.now();
        Task task = schedulableTask("Geplante Task", today, LocalTime.of(9, 0));
        taskDao.write(task);

        // First, a real run creates the plan.
        CallbackProbe<RegenerateScheduleUseCase.Result> first = new CallbackProbe<>();
        realRegenerate().execute(first.consumer());
        assertTrue(first.value().createdSlots() > 0);
        int slotsBefore = taskDao.read(task.core.id).slots.size();
        assertTrue(slotsBefore > 0);

        // Now a run whose generator throws must not destroy that plan.
        RegenerateScheduleUseCase failing = new RegenerateScheduleUseCase(
                db, taskDao, new ThrowingGenerator(), exec, exec, () -> true);
        CallbackProbe<RegenerateScheduleUseCase.Result> second = new CallbackProbe<>();
        failing.execute(second.consumer());
        assertEquals("failed run reports empty", 0, second.value().createdSlots());

        int slotsAfter = taskDao.read(task.core.id).slots.size();
        assertEquals("the existing plan is preserved on failure (transaction rolled back)",
                slotsBefore, slotsAfter);
    }

    private RegenerateScheduleUseCase realRegenerate() {
        return new RegenerateScheduleUseCase(db, taskDao, realGenerator(), exec, exec, () -> true);
    }

    private TaskSlotGenerator realGenerator() {
        return DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao()),
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                () -> List.of(),
                candidate -> true,
                () -> SchedulingTuning.NONE);
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

    /** Generator that fails mid-run to exercise the transaction rollback. */
    private static final class ThrowingGenerator implements TaskSlotGenerator {
        @Override
        public void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive,
                                         LocalDate endExclusive, TaskPlanningState state) { }

        @Override
        public TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day,
                                                            TaskPlanningState state) {
            throw new IllegalStateException("boom");
        }

        @Override
        public TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay,
                                                               int days, TaskPlanningState state) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void recordScheduledSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) { }
    }
}
