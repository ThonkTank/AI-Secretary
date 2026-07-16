package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.SynchronousExecutorService;
import com.autosecretary.testing.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for {@link ScheduleReplanCoordinator}: it must coalesce a burst of re-plan requests
 * into a single follow-up run, and notify its listener once per completed run.
 */
public final class ScheduleReplanCoordinatorTest extends AutoSecretaryRobolectricTest {
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
     * Invariant: requests arriving while a re-plan is running collapse into exactly one follow-up
     * run — three requests around a single in-flight run produce two runs total, not three.
     */
    @Test
    public void concurrentRequestsCoalesceIntoOneFollowUpRunInvariant() {
        ManualExecutor worker = new ManualExecutor();
        AtomicInteger runs = new AtomicInteger();
        // worker defers the regen body; callback dispatcher runs inline so onDone re-enters the
        // coordinator synchronously when the body eventually runs.
        SynchronousExecutorService inline = new SynchronousExecutorService();
        RegenerateScheduleUseCase regenerate = new RegenerateScheduleUseCase(
                db, taskDao, new CountingGenerator(runs), worker, inline, () -> true);
        ScheduleReplanCoordinator coordinator = new ScheduleReplanCoordinator(regenerate, noWidgets());

        coordinator.requestReplan();   // starts run 1 (body queued on worker)
        coordinator.requestReplan();   // running → marks a follow-up
        coordinator.requestReplan();   // running → follow-up already marked (idempotent)
        assertEquals("only the first run has been enqueued so far", 1, worker.pending());

        worker.runNext();              // run 1 completes → starts the single coalesced run 2
        assertEquals("exactly one follow-up run was started", 1, worker.pending());
        assertEquals(1, runs.get());

        worker.runNext();              // run 2 completes → no further follow-up
        assertEquals("no third run", 0, worker.pending());
        assertEquals("two runs total for three coalesced requests", 2, runs.get());
    }

    /** Invariant: the registered listener is notified once per completed re-plan, then not after clearing. */
    @Test
    public void listenerIsNotifiedPerRunAndClearedInvariant() {
        SynchronousExecutorService inline = new SynchronousExecutorService();
        AtomicInteger runs = new AtomicInteger();
        RegenerateScheduleUseCase regenerate = new RegenerateScheduleUseCase(
                db, taskDao, new CountingGenerator(runs), inline, inline, () -> true);
        ScheduleReplanCoordinator coordinator = new ScheduleReplanCoordinator(regenerate, noWidgets());

        AtomicInteger notified = new AtomicInteger();
        java.util.function.Consumer<RegenerateScheduleUseCase.Result> listener = r -> notified.incrementAndGet();
        coordinator.setListener(listener);

        coordinator.requestReplan();   // runs fully inline
        coordinator.requestReplan();
        assertEquals("listener fired once per run", 2, notified.get());

        coordinator.clearListener(listener);
        coordinator.requestReplan();
        assertEquals("no notification after clearing", 2, notified.get());
    }

    private static WidgetRefreshNotifier noWidgets() {
        return new WidgetRefreshNotifier() {
            @Override public void refreshTaskWidgets() { }
            @Override public void refreshBudgetWidgets() { }
        };
    }

    /** Executor that queues submitted commands and runs them one at a time on demand. */
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean shutdown;

        int pending() {
            return queue.size();
        }

        void runNext() {
            Runnable next = queue.poll();
            if (next != null) {
                next.run();
            }
        }

        @Override public void execute(Runnable command) { queue.add(command); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    /** Generator that produces nothing but counts each window generation (one per re-plan run). */
    private static final class CountingGenerator implements TaskSlotGenerator {
        private final AtomicInteger runs;

        CountingGenerator(AtomicInteger runs) {
            this.runs = runs;
        }

        @Override
        public void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive,
                                         LocalDate endExclusive, TaskPlanningState state) { }

        @Override
        public TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day,
                                                            TaskPlanningState state) {
            return new TaskSlotGenerationResult(0, List.of());
        }

        @Override
        public TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay,
                                                               int days, TaskPlanningState state) {
            runs.incrementAndGet();
            return new TaskSlotGenerationResult(0, List.of());
        }

        @Override
        public void recordScheduledSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state) { }
    }
}
