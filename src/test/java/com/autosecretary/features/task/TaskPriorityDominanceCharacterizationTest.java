package com.autosecretary.features.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
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
 * Protects the placement-ordering invariants around task priority.
 *
 * <p>Phase 1 (stable across the priority rework): TERMIN slots are never displaced by normal
 * tasks; started/completed slots survive regeneration; the repetition quota holds across the
 * window. Phase 2 (the rework's new guarantees): priority is the dominant ordering principle —
 * a higher-priority task is placed before any lower-priority one and can never be displaced by
 * one, regardless of urgency inflation; overdue urgency ramps instead of cliffing.
 */
public final class TaskPriorityDominanceCharacterizationTest extends AutoSecretaryRobolectricTest {
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

    private static TaskSlotGenerator generator(SchedulingWindowProvider windowProvider) {
        return DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                windowProvider,
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                List::of,
                candidate -> true,
                () -> SchedulingTuning.NONE);
    }

    private static SchedulingWindowProvider window(LocalTime start, LocalTime end) {
        return d -> new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(d, start), LocalDateTime.of(d, end));
    }

    private static Task dailyTask(String title, LocalDate day, Priority priority, int durationMinutes) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.priority = priority;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        task.core.minDuration = durationMinutes;
        task.core.maxDuration = durationMinutes;
        TaskFixtures.prefSlot(task, day, LocalTime.of(9, 0));
        return task;
    }

    private static Task weeklyTask(String title, LocalDate day, Priority priority, int durationMinutes) {
        Task task = dailyTask(title, day, priority, durationMinutes);
        task.core.repetition.periodUnit = Period.WEEK;
        return task;
    }

    /** Overdue task: deadline in the past, closeOnMiss off so it stays schedulable. */
    private static Task overdueTask(String title, LocalDate day, Priority priority,
                                    int durationMinutes, int overdueDays) {
        Task task = weeklyTask(title, day, priority, durationMinutes);
        task.core.deadline = day.minusDays(overdueDays);
        task.core.closeOnMiss = false;
        return task;
    }

    private static boolean hasScheduledSlot(Task task) {
        return task.slots.stream().anyMatch(slot -> slot.scheduled);
    }

    // ---------------------------------------------------------------------
    // Phase 1 — invariants that must hold before AND after the rework
    // ---------------------------------------------------------------------

    @Test
    public void terminSlotsAreNeverDisplacedByNormalTasksInvariant() {
        LocalDate today = LocalDate.now();
        Task appointment = new Task();
        appointment.core.title = "Fixer Termin";
        appointment.core.created = today;
        appointment.core.schedulingType = TaskCore.SchedulingType.TERMIN;
        appointment.core.fixedDate = today;
        appointment.core.fixedStart = LocalTime.of(9, 0);
        appointment.core.fixedEnd = LocalTime.of(10, 0);
        appointment.core.repetition.perPeriod = 1;
        appointment.core.repetition.periodUnit = Period.DAY;
        appointment.core.repetition.periodStart = today;

        Task critical = dailyTask("Kritische Aufgabe", today, Priority.CRITICAL, 60);

        TaskSlotGenerator generator = generator(window(LocalTime.of(9, 0), LocalTime.of(10, 0)));
        generator.generateSlotsForWindow(List.of(appointment, critical), today, 1, new TaskPlanningState());

        assertTrue("the TERMIN keeps its pinned slot", hasScheduledSlot(appointment));
        assertFalse("even a CRITICAL normal task cannot displace a TERMIN", hasScheduledSlot(critical));
    }

    @Test
    public void startedOrCompletedSlotsSurviveRegenerationInvariant() {
        LocalDate today = LocalDate.now();
        Task task = TaskFixtures.taskWithSlot("Begonnene Aufgabe", today);
        TaskSlot started = task.slots.get(0);
        started.realStart = LocalTime.of(10, 0);
        taskDao.write(task);

        SynchronousExecutorService executor = new SynchronousExecutorService();
        TaskSlotGenerator generator = DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao()),
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                List::of,
                candidate -> true,
                () -> SchedulingTuning.NONE);
        RegenerateScheduleUseCase useCase = new RegenerateScheduleUseCase(
                db, taskDao, generator, executor, executor, () -> true);

        CallbackProbe<RegenerateScheduleUseCase.Result> probe = new CallbackProbe<>();
        useCase.execute(probe.consumer());

        Task persisted = taskDao.read(task.core.id);
        assertTrue("the started slot survives regeneration",
                persisted.slots.stream().anyMatch(slot ->
                        slot.id.equals(started.id) && slot.realStart != null));
    }

    @Test
    public void repetitionQuotaIsNotExceededAcrossWindowInvariant() {
        LocalDate today = LocalDate.now();
        Task twicePerWeek = weeklyTask("Zweimal pro Woche", today, Priority.MEDIUM, 30);
        twicePerWeek.core.repetition.reps = 2;

        TaskSlotGenerator generator = generator(window(LocalTime.of(9, 0), LocalTime.of(17, 0)));
        generator.generateSlotsForWindow(List.of(twicePerWeek), today, 7, new TaskPlanningState());

        long scheduled = twicePerWeek.slots.stream().filter(slot -> slot.scheduled).count();
        assertTrue("at most 2 slots for a 2-per-week task in the 7-day window (was " + scheduled + ")",
                scheduled <= 2);
    }

    // ---------------------------------------------------------------------
    // Phase 2 — the rework's new guarantees (priority dominance, urgency ramp)
    // ---------------------------------------------------------------------

    @Test
    public void criticalTaskIsPlacedEvenWhenDayIsSaturatedWithOverdueLowTasksInvariant() {
        LocalDate today = LocalDate.now();
        Task overdueLow1 = overdueTask("Überfällig 1", today, Priority.LOW, 30, 5);
        Task overdueLow2 = overdueTask("Überfällig 2", today, Priority.LOW, 30, 5);
        Task critical = dailyTask("Kritisch", today, Priority.CRITICAL, 30);

        TaskSlotGenerator generator = generator(window(LocalTime.of(9, 0), LocalTime.of(10, 0)));
        generator.generateSlotsForWindow(
                List.of(overdueLow1, overdueLow2, critical), today, 1, new TaskPlanningState());

        assertTrue("the CRITICAL task gets a slot despite overdue LOW competition",
                hasScheduledSlot(critical));
    }

    @Test
    public void overdueLowTaskNeverDisplacesHighTaskInvariant() {
        LocalDate today = LocalDate.now();
        Task high = weeklyTask("Wichtig", today, Priority.HIGH, 60);
        Task overdueLow = overdueTask("Überfällig niedrig", today, Priority.LOW, 60, 5);

        // The window fits exactly one 60-minute slot — the only way in is displacement.
        TaskSlotGenerator generator = generator(window(LocalTime.of(9, 0), LocalTime.of(10, 0)));
        generator.generateSlotsForWindow(List.of(high, overdueLow), today, 1, new TaskPlanningState());

        assertTrue("the HIGH task keeps the only slot", hasScheduledSlot(high));
        assertFalse("the overdue LOW task cannot displace it regardless of urgency inflation",
                hasScheduledSlot(overdueLow));
    }

    @Test
    public void overdueUrgencyRampsInsteadOfCliffInvariant() {
        LocalDate today = LocalDate.now();
        Task slightlyOverdue = overdueTask("Kaum überfällig", today, Priority.MEDIUM, 30, 1);
        Task veryOverdue = overdueTask("Lange überfällig", today, Priority.MEDIUM, 30, 5);

        // One 30-minute gap: the more-overdue task must win it.
        TaskSlotGenerator generator = generator(window(LocalTime.of(9, 0), LocalTime.of(9, 30)));
        generator.generateSlotsForWindow(
                List.of(slightlyOverdue, veryOverdue), today, 1, new TaskPlanningState());

        assertTrue("the more-overdue task wins the single gap", hasScheduledSlot(veryOverdue));
        assertFalse("the barely-overdue task loses the single gap", hasScheduledSlot(slightlyOverdue));
    }
}
