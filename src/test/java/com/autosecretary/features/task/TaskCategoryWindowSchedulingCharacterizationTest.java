package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Protects the category time-window scheduling invariants (two-pass soft-exclusive placement) and
 * the global scheduling on/off toggle.
 */
public final class TaskCategoryWindowSchedulingCharacterizationTest extends AutoSecretaryRobolectricTest {
    private static final String CATEGORY_A = "cat-a";
    private static final String CATEGORY_B = "cat-b";

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
     * Invariant (exclusivity): inside a category-A window, pass 1 places only category-A tasks. When
     * A tasks saturate the reserved time, a category-B task is locked out entirely.
     */
    @Test
    public void categoryWindowPlacesOnlyItsCategoryWhenReservedTimeIsSaturated() {
        LocalDate today = LocalDate.now();
        // Outer window 09:00–10:00 (two 30-min slots), entirely reserved for category A.
        Task a1 = categoryTask("A1", CATEGORY_A, today);
        Task a2 = categoryTask("A2", CATEGORY_A, today);
        Task b1 = categoryTask("B1", CATEGORY_B, today);

        TaskSlotGenerator generator = generator(
                window(today, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                reserve(CATEGORY_A, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        generator.generateSlotsForWindow(List.of(a1, a2, b1), today, 1, new TaskPlanningState());

        assertTrue("category-A task A1 fills its reserved window", hasScheduledSlot(a1));
        assertTrue("category-A task A2 fills its reserved window", hasScheduledSlot(a2));
        assertFalse("category-B task is locked out of the saturated A window", hasScheduledSlot(b1));
    }

    /**
     * Invariant (no-waste fallback): reserved time left over after pass 1 places the matching
     * category is filled in pass 2 by a task of another category.
     */
    @Test
    public void leftoverReservedTimeIsFilledByAnotherCategoryInPassTwo() {
        LocalDate today = LocalDate.now();
        // Outer window 09:00–10:00 (two slots), reserved for A, but only one A task exists.
        Task a1 = categoryTask("A1", CATEGORY_A, today);
        Task b1 = categoryTask("B1", CATEGORY_B, today);

        TaskSlotGenerator generator = generator(
                window(today, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                reserve(CATEGORY_A, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        generator.generateSlotsForWindow(List.of(a1, b1), today, 1, new TaskPlanningState());

        assertTrue("category-A task takes the reserved window first", hasScheduledSlot(a1));
        assertTrue("leftover reserved time is filled by the category-B task in pass 2", hasScheduledSlot(b1));
    }

    /**
     * Invariant (backward compatibility): with no category windows defined the scheduler behaves
     * exactly as before — a single free window, one pass — placing both tasks regardless of category.
     */
    @Test
    public void withoutCategoryWindowsBothTasksScheduleAsBefore() {
        LocalDate today = LocalDate.now();
        Task a1 = categoryTask("A1", CATEGORY_A, today);
        Task b1 = categoryTask("B1", CATEGORY_B, today);

        TaskSlotGenerator generator = generator(
                window(today, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                CategoryWindowProvider.NONE);
        generator.generateSlotsForWindow(List.of(a1, b1), today, 1, new TaskPlanningState());

        assertTrue(hasScheduledSlot(a1));
        assertTrue(hasScheduledSlot(b1));
    }

    /**
     * Invariant (global toggle): when daily planning is disabled, regeneration clears the checklist
     * (removes regeneratable slots in the window) and generates zero new slots.
     */
    @Test
    public void disabledSchedulingClearsWindowAndCreatesNoSlots() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Bestehend", today); // pre-existing scheduled slot today
        taskDao.write(task);

        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskSlotGenerator generator = generator(
                window(today, LocalTime.of(9, 0), LocalTime.of(11, 0)),
                CategoryWindowProvider.NONE);
        RegenerateScheduleUseCase useCase = new RegenerateScheduleUseCase(
                taskDao, generator, executor, executor, () -> false); // scheduling OFF

        CallbackProbe<RegenerateScheduleUseCase.Result> probe = new CallbackProbe<>();
        useCase.execute(probe.consumer());

        assertEquals(0, probe.value().createdSlots());
        Task persisted = taskDao.read(task.core.id);
        assertFalse("regeneratable slots in the window are cleared when scheduling is off",
                persisted.slots.stream().anyMatch(slot -> slot.scheduled && !slot.day.isBefore(today)));
    }

    private Task categoryTask(String title, String categoryId, LocalDate day) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.categoryId = categoryId;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        task.core.minDuration = 30;
        task.core.maxDuration = 30;
        TaskFixtures.prefSlot(task, day, LocalTime.of(9, 0));
        return task;
    }

    private static SchedulingWindowProvider window(LocalDate day, LocalTime start, LocalTime end) {
        return d -> new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(d, start), LocalDateTime.of(d, end));
    }

    private static CategoryWindowProvider reserve(String categoryId, LocalTime start, LocalTime end) {
        return d -> List.of(new CategoryWindowProvider.CategoryWindow(categoryId, start, end));
    }

    private static TaskSlotGenerator generator(SchedulingWindowProvider windowProvider,
                                               CategoryWindowProvider categoryWindowProvider) {
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
}
