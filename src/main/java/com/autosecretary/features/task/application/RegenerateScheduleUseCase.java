package com.autosecretary.features.task.application;

import androidx.room.RoomDatabase;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;

import android.util.Log;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Entry point for schedule generation. Reads all tasks from the database, generates
 * a 7-day schedule (today + 6 days) using {@link TaskSlotGenerator} with cross-day state
 * tracking, and writes scheduled results back.
 *
 * <strong>Threading contract:</strong> DB work runs on the provided {@link ExecutorService};
 * the {@code onDone} callback is dispatched via {@code callbackDispatcher} (typically main/UI).
 */
public class RegenerateScheduleUseCase {
    private static final int PLANNING_DAYS = 7;

    /**
     * Summary of a completed scheduling run.
     *
     * <p>{@code createdSlots} is the total number of new slots written across the entire
     * 7-day planning window. {@code conflicts} lists every task/day combination where the
     * scheduler could not place a slot (e.g. outside the scheduling window, calendar blocked,
     * or prerequisite not met). An empty conflict list means all tasks were placed successfully.
     */
    public record Result(int createdSlots, List<SchedulingConflict> conflicts) {}

    private final RoomDatabase database;
    private final TaskDao taskDao;
    private final TaskSlotGenerator generator;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;
    private final BooleanSupplier schedulingEnabled;

    public RegenerateScheduleUseCase(RoomDatabase database,
                                     TaskDao taskDao,
                                     TaskSlotGenerator generator,
                                     ExecutorService workerExecutor,
                                     Executor callbackDispatcher,
                                     BooleanSupplier schedulingEnabled) {
        this.database = database;
        this.taskDao = taskDao;
        this.generator = generator;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
        this.schedulingEnabled = schedulingEnabled;
    }

    public void execute(Consumer<Result> onDone) {
        workerExecutor.execute(() -> {
            try {
                LocalDate today = LocalDate.now();
                LocalDate windowEnd = today.plusDays(PLANNING_DAYS);

                // Delete + read + generate + write run in ONE transaction so a failure (e.g. a
                // malformed task) rolls back the delete instead of leaving the plan wiped. The
                // holder carries the result out of the Runnable.
                Result[] holder = { new Result(0, Collections.emptyList()) };
                database.runInTransaction(() -> {
                    // Remove stale slots first so readAll() returns the true baseline.
                    // Preserves started/completed work; only untouched scheduled slots are regenerated.
                    taskDao.deleteRegeneratableSlotsInWindow(today, windowEnd);

                    // Global toggle off: leave the checklist cleared (stale slots removed above) and
                    // skip generation entirely, so daily planning can be disabled without losing data.
                    if (!schedulingEnabled.getAsBoolean()) {
                        return;
                    }

                    List<Task> tasks = taskDao.readAll();

                    TaskPlanningState state = new TaskPlanningState();
                    generator.recordPreservedSlots(tasks, today, windowEnd, state);

                    // Tasks are flat (grouped only by category), so no tree building is needed;
                    // the scheduler places each task independently.
                    TaskSlotGenerationResult generationResult =
                            generator.generateSlotsForWindow(tasks, today, PLANNING_DAYS, state);

                    taskDao.writeList(tasks);
                    holder[0] = new Result(generationResult.createdSlots(), generationResult.conflicts());
                });
                Result result = holder[0];
                callbackDispatcher.execute(() -> onDone.accept(result));
            } catch (Exception e) {
                // Transaction rolled back — the previous plan is intact. Report an empty run.
                Log.e("RegenerateSchedule", "Schedule regeneration failed", e);
                callbackDispatcher.execute(() -> onDone.accept(new Result(0, Collections.emptyList())));
            }
        });
    }
}
