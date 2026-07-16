package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.CallbackProbe;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Regression tests for the "60 Aufgaben konnten nicht eingeplant werden" incident: assistant-created
 * recurring chores never got a slot because two hard gates meant for one-off tasks wrongly excluded
 * fresh recurring tasks —
 * <ul>
 *   <li>the cooldown gate: a never-completed task's {@code lastCompletion} defaults to
 *       {@code created − 1 day}, so any {@code cooldown} larger than "days since creation" blocked it
 *       (the assistant set {@code cooldown} = period length on every task);</li>
 *   <li>the deadline-expiry gate: {@code closeOnMiss} + a stale (past) deadline permanently excluded a
 *       recurring task, even though the task rolls with its period.</li>
 * </ul>
 * These drive the real generator over exactly that data and assert the task is scheduled, while the
 * post-completion cooldown behaviour (which must stay intact) is separately guarded.
 */
public final class FreshRecurringTaskSchedulingCharacterizationTest extends AutoSecretaryRobolectricTest {
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

    /** Cooldown gate: a fresh weekly chore with cooldown = period length must still be schedulable. */
    @Test
    public void freshRecurringChoreWithPeriodLengthCooldownSchedulesInvariant() {
        Task chore = recurringChore("Bad putzen", Period.WEEK, 1, 1);
        chore.core.cooldown = 7; // = period length, as the assistant set it — used to block for 6 days
        taskDao.write(chore);

        assertTrue("a fresh recurring chore must schedule despite a period-length cooldown",
                regenerate().createdSlots() > 0);
    }

    /** Deadline-expiry gate: a recurring chore with a stale past deadline + closeOnMiss still schedules. */
    @Test
    public void recurringChoreWithPastDeadlineAndCloseOnMissSchedulesInvariant() {
        LocalDate today = LocalDate.now();
        Task chore = recurringChore("Küche putzen", Period.WEEK, 1, 1);
        chore.core.cooldown = 0;
        chore.core.closeOnMiss = true;
        chore.core.deadline = today.minusDays(30); // long past — used to permanently exclude it
        taskDao.write(chore);

        assertTrue("a recurring chore rolls with its period and must not expire on a stale deadline",
                regenerate().createdSlots() > 0);
    }

    /** The exact real-world combination (both blockers on one task) must still schedule. */
    @Test
    public void realWorldRecurringChoreWithBothBlockersSchedulesInvariant() {
        LocalDate today = LocalDate.now();
        Task chore = recurringChore("Pflanzen gießen", Period.DAY, 1, 1);
        chore.core.cooldown = 4;                       // > days since creation
        chore.core.closeOnMiss = true;
        chore.core.deadline = today.minusDays(3);      // past
        taskDao.write(chore);

        assertTrue("a fresh recurring chore with a period cooldown and a stale deadline must schedule",
                regenerate().createdSlots() > 0);
    }

    /**
     * The cooldown fix must be narrow: once a task HAS been completed, cooldown still spaces the next
     * placement. A daily chore completed today with a large cooldown gets no new slot in the window.
     */
    @Test
    public void cooldownStillBlocksAfterACompletionInvariant() {
        LocalDate today = LocalDate.now();
        Task chore = recurringChore("Gesicht rasieren", Period.DAY, 1, 1);
        chore.core.cooldown = 30; // spans the whole regeneration window
        TaskSlot done = new TaskSlot();
        done.taskId = chore.core.id;
        done.day = today;
        done.start = java.time.LocalTime.of(9, 0);
        done.end = java.time.LocalTime.of(9, 30);
        done.scheduled = true;
        done.completed = true;
        chore.slots.add(done);
        taskDao.write(chore);

        RegenerateScheduleUseCase.Result result = regenerate();
        assertEquals("cooldown still blocks a task completed within the cooldown window",
                0, result.createdSlots());
        assertTrue("the completed slot is preserved (not wiped by regeneration)",
                taskDao.read(chore.core.id).slots.stream().anyMatch(s -> s.completed));
    }

    /**
     * Honest conflict reporting: only tasks that are schedulable in principle but could not be placed
     * count as conflicts. A reps=0 one-off (never auto-scheduled) must not inflate the count — that
     * conflation is what turned a handful of genuine no-room cases into "60 Aufgaben".
     */
    @Test
    public void nonSchedulableOneOffIsNotReportedAsConflictInvariant() {
        // Placeable recurring chore: gets a slot, not a conflict.
        taskDao.write(recurringChore("Wäsche waschen", Period.WEEK, 1, 1));
        // reps=0 one-off: never auto-scheduled → must be absent from the conflict list.
        Task oneOff = recurringChore("Fenster streichen", Period.WEEK, 1, 1);
        oneOff.core.repetition.reps = 0;
        taskDao.write(oneOff);
        // Genuine no-room: a recurring chore that cannot fit any gap (needs more than the whole window).
        Task tooBig = recurringChore("Umzug", Period.WEEK, 1, 1);
        tooBig.core.minDuration = 100_000;
        tooBig.core.maxDuration = 100_000;
        taskDao.write(tooBig);

        List<SchedulingConflict> conflicts = regenerate().conflicts();
        assertTrue("the reps=0 one-off must not be reported as a scheduling conflict",
                conflicts.stream().noneMatch(c -> "Fenster streichen".equals(c.title())));
        assertTrue("the genuinely unplaceable recurring chore is still reported",
                conflicts.stream().anyMatch(c -> "Umzug".equals(c.title())));
    }

    private RegenerateScheduleUseCase.Result regenerate() {
        TaskSlotGenerator generator = DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                new TaskScheduleConfigRepository(db.taskScheduleConfigDao()),
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                () -> List.of(),
                candidate -> true,
                () -> SchedulingTuning.NONE);
        CallbackProbe<RegenerateScheduleUseCase.Result> probe = new CallbackProbe<>();
        new RegenerateScheduleUseCase(db, taskDao, generator, exec, exec, () -> true).execute(probe.consumer());
        return probe.value();
    }

    /** A well-formed recurring chore with NO pref slot (mirrors the assistant-created data). */
    private static Task recurringChore(String title, Period periodUnit, int reps, int perPeriod) {
        Task task = new Task();
        LocalDate today = LocalDate.now();
        task.core.title = title;
        task.core.created = today;
        task.core.repetition.reps = reps;
        task.core.repetition.perPeriod = perPeriod;
        task.core.repetition.periodUnit = periodUnit;
        task.core.repetition.periodStart = today;
        task.core.minDuration = 30;
        task.core.maxDuration = 30;
        return task;
    }
}
