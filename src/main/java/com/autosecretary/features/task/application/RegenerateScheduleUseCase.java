package com.autosecretary.features.task.application;

import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.scheduling.SchedulingConflict;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.features.task.domain.TaskTreeOperations;

import android.util.Log;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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

    private final TaskDao taskDao;
    private final TaskSlotGenerator generator;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public RegenerateScheduleUseCase(TaskDao taskDao,
                                     TaskSlotGenerator generator,
                                     ExecutorService workerExecutor,
                                     Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.generator = generator;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void execute(Consumer<Result> onDone) {
        workerExecutor.execute(() -> {
            try {
                LocalDate today = LocalDate.now();
                LocalDate windowEnd = today.plusDays(PLANNING_DAYS);

                // Remove stale slots in DB first so readAll() returns the true baseline.
                // Preserves started/completed work; only untouched scheduled slots are regenerated.
                taskDao.deleteRegeneratableSlotsInWindow(today, windowEnd);
                List<Task> tasks = taskDao.readAll();

                TaskPlanningState state = new TaskPlanningState();
                generator.recordPreservedSlots(tasks, today, windowEnd, state);

                // Build the task hierarchy tree to establish parent-child ordering and
                // propagate parent priority/constraints to children. The result is then
                // immediately flattened because generateSlotsForWindow expects a flat list
                // in depth-first traversal order (children after their parent).
                List<Task> flatTasks = TaskTreeOperations.flatten(
                        TaskTreeOperations.buildTree(tasks));
                TaskSlotGenerationResult generationResult =
                        generator.generateSlotsForWindow(flatTasks, today, PLANNING_DAYS, state);

                taskDao.writeList(flatTasks);
                Result result = new Result(generationResult.createdSlots(), generationResult.conflicts());
                callbackDispatcher.execute(() -> onDone.accept(result));
            } catch (Exception e) {
                Log.e("RegenerateSchedule", "Schedule regeneration failed", e);
                callbackDispatcher.execute(() -> onDone.accept(new Result(0, Collections.emptyList())));
            }
        });
    }
}
