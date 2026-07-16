package com.autosecretary.testing;

import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.RegenerateScheduleUseCase;
import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.WidgetRefreshNotifier;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test builders for {@link ScheduleReplanCoordinator}.
 *
 * <ul>
 *   <li>{@link #inert()} — a coordinator whose {@code requestReplan()} does nothing (its underlying
 *       re-plan is never executed). Use in tests that drive a mutation path but don't assert on
 *       re-planning; it keeps the DB untouched.</li>
 *   <li>{@link #recording(AppDatabase)} — a coordinator wired to a real {@link RegenerateScheduleUseCase}
 *       that runs synchronously against a no-op generator, counting how many times a re-plan ran.
 *       Use to assert that a trigger fired.</li>
 * </ul>
 */
public final class ReplanCoordinators {

    private static final WidgetRefreshNotifier NO_WIDGETS = new WidgetRefreshNotifier() {
        @Override public void refreshTaskWidgets() { }
        @Override public void refreshBudgetWidgets() { }
    };

    private ReplanCoordinators() {}

    /** A coordinator that swallows every re-plan request without side effects. */
    public static ScheduleReplanCoordinator inert() {
        // The discarding executor never runs the body, so the null database is never touched.
        RegenerateScheduleUseCase noop = new RegenerateScheduleUseCase(
                null, null, null, new DiscardingExecutorService(), r -> {}, () -> false);
        return new ScheduleReplanCoordinator(noop, NO_WIDGETS);
    }

    /** A coordinator counting re-plan runs; drives a real {@link RegenerateScheduleUseCase} synchronously. */
    public static Recording recording(AppDatabase db) {
        AtomicInteger runs = new AtomicInteger();
        SynchronousExecutorService exec = new SynchronousExecutorService();
        RegenerateScheduleUseCase regenerate = new RegenerateScheduleUseCase(
                db, db.taskDao(), new CountingNoopGenerator(runs), exec, exec, () -> true);
        return new Recording(new ScheduleReplanCoordinator(regenerate, NO_WIDGETS), runs);
    }

    /** A coordinator plus a live count of how many re-plans it has run. */
    public static final class Recording {
        public final ScheduleReplanCoordinator coordinator;
        private final AtomicInteger runs;

        Recording(ScheduleReplanCoordinator coordinator, AtomicInteger runs) {
            this.coordinator = coordinator;
            this.runs = runs;
        }

        public int runs() {
            return runs.get();
        }
    }

    /** Generator that produces nothing but counts each window generation (i.e. each re-plan). */
    private static final class CountingNoopGenerator implements TaskSlotGenerator {
        private final AtomicInteger runs;

        CountingNoopGenerator(AtomicInteger runs) {
            this.runs = runs;
        }

        @Override
        public void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive,
                                         LocalDate endExclusive, TaskPlanningState state) { }

        @Override
        public TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day,
                                                            TaskPlanningState state) {
            runs.incrementAndGet();
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

    /** Executor that drops every submitted command — used to make {@link #inert()} truly side-effect free. */
    private static final class DiscardingExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { /* discarded */ }
    }
}
