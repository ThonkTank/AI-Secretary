package com.autosecretary.features.task.application;

import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Async wrapper around {@link TaskDao}.
 * <p>
 * <strong>Threading contract:</strong>
 * <ul>
 *     <li>All DAO work is executed on the provided worker {@link ExecutorService}.</li>
 *     <li>Every completion callback ({@code callback.accept(...)} / {@code onSaved.run()}) is
 *     dispatched via {@link #callbackDispatcher}.</li>
 * </ul>
 * Callers can therefore assume callbacks are invoked on the dispatcher thread (typically main/UI).
 */
public class TaskDataService {
    private final TaskDao taskDao;
    private final TaskListItemMapper mapper;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;
    private final MealRepository mealRepository;

    public TaskDataService(TaskDao taskDao,
                           TaskListItemMapper mapper,
                           ExecutorService workerExecutor,
                           Executor callbackDispatcher,
                           MealRepository mealRepository) {
        this.taskDao = taskDao;
        this.mapper = mapper;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
        this.mealRepository = mealRepository;
    }

    /**
     * Loads all tasks and maps them to {@link TaskListItem} presentation models.
     *
     * @param onLoaded receives the flat mapped list on the callback dispatcher thread;
     *                 never null, but may be empty if no tasks exist
     */
    public void loadAllMapped(Consumer<List<TaskListItem>> onLoaded) {
        workerExecutor.execute(() -> {
            List<TaskListItem> items = mapper.map(taskDao.readAll());
            callbackDispatcher.execute(() -> onLoaded.accept(items));
        });
    }

    /**
     * Loads a single task by its UUID.
     *
     * @param id       the task UUID
     * @param onLoaded receives the loaded {@link com.autosecretary.features.task.data.Task}
     *                 on the callback dispatcher thread, or {@code null} if not found
     */
    public void loadTask(String id, Consumer<Task> onLoaded) {
        workerExecutor.execute(() -> {
            Task task = taskDao.read(id);
            callbackDispatcher.execute(() -> onLoaded.accept(task));
        });
    }

    /**
     * Persists a task (insert or replace).
     *
     * @param task    the task to write
     * @param onSaved callback dispatched on the callback dispatcher thread after the write
     */
    public void saveTask(Task task, Runnable onSaved) {
        workerExecutor.execute(() -> {
            taskDao.write(task);
            callbackDispatcher.execute(onSaved);
        });
    }

    /**
     * Deletes a task and its entire graph (slots, relations, prerequisites, planned meals).
     *
     * @param taskId    the UUID of the task to delete
     * @param onDeleted callback dispatched on the callback dispatcher thread after deletion
     */
    public void deleteTask(String taskId, Runnable onDeleted) {
        workerExecutor.execute(() -> {
            unlinkMealPlan(taskId);
            taskDao.deleteTaskGraph(taskId);
            callbackDispatcher.execute(onDeleted);
        });
    }

    /**
     * Synchronous full read. Must only be called from a worker-thread context.
     */
    public List<Task> readAllSync() {
        return taskDao.readAll();
    }

    /** Clears the back-link on the associated meal plan (if any) before task deletion. */
    private void unlinkMealPlan(String taskId) {
        MealPlan plan = mealRepository.findMealPlanByItemId(taskId);
        if (plan != null) {
            plan.itemId = null;
            mealRepository.saveMealPlan(plan);
        }
    }
}
