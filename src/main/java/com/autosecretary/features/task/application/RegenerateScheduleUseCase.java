package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.internal.TaskSeedDataFactory;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.SchedulingConflict;
import com.autosecretary.features.task.domain.TaskPlanningState;
import com.autosecretary.features.task.domain.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.TaskSlotGenerator;
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
 * tracking, and writes scheduled results back. Seeds default tasks on first run when
 * the DB is empty.
 *
 * <strong>Threading contract:</strong> DB work runs on the provided {@link ExecutorService};
 * the {@code onDone} callback is dispatched via {@code callbackDispatcher} (typically main/UI).
 */
public class RegenerateScheduleUseCase {
    private static final int PLANNING_DAYS = 7;

    public static class Result {
        public final int createdSlots;
        public final List<SchedulingConflict> conflicts;

        public Result(int createdSlots, List<SchedulingConflict> conflicts) {
            this.createdSlots = createdSlots;
            this.conflicts = conflicts;
        }
    }

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
                List<Task> tasks = taskDao.readAll();
                if (tasks.isEmpty()) {
                    List<Task> seedTasks = TaskSeedDataFactory.createDefaultTasks();
                    taskDao.writeList(TaskTreeOperations.flatten(seedTasks));
                    tasks = taskDao.readAll();
                }

                LocalDate today = LocalDate.now();
                LocalDate windowEnd = today.plusDays(PLANNING_DAYS);

                for (Task task : tasks) {
                    task.slots.removeIf(slot ->
                            !slot.completed
                                    && slot.realStart == null
                                    && slot.scheduled
                                    && slot.day != null
                                    && !slot.day.isBefore(today)
                                    && slot.day.isBefore(windowEnd));
                }

                TaskPlanningState state = new TaskPlanningState();
                generator.recordPreservedSlots(tasks, today, windowEnd, state);

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
