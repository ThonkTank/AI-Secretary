package com.autosecretary.features.task.application;

import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;

import java.time.LocalTime;
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

    public TaskDataService(TaskDao taskDao,
                           TaskListItemMapper mapper,
                           ExecutorService workerExecutor,
                           Executor callbackDispatcher) {
        this.taskDao = taskDao;
        this.mapper = mapper;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public void loadAllMapped(Consumer<List<TaskListItem>> callback) {
        workerExecutor.execute(() -> {
            List<TaskListItem> items = mapper.map(taskDao.readAll());
            callbackDispatcher.execute(() -> callback.accept(items));
        });
    }

    public void loadTask(String id, Consumer<Task> callback) {
        workerExecutor.execute(() -> {
            Task task = taskDao.read(id);
            callbackDispatcher.execute(() -> callback.accept(task));
        });
    }

    public void saveTask(Task task, Runnable onSaved) {
        workerExecutor.execute(() -> {
            taskDao.write(task);
            callbackDispatcher.execute(onSaved);
        });
    }

    /**
     * Starts a manual timer for a slot by recording the current time as its start.
     *
     * <p><strong>Not the same as the two-phase check-off:</strong> The two-phase check-off
     * (first tap → STARTED, second tap → COMPLETED) is handled by {@link CheckOffTaskUseCase}.
     * This method is a separate feature for explicit manual time tracking — the user explicitly
     * starts and stops a timer independent of the completion flow.
     *
     * @param slotId  the slot UUID to start timing
     * @param onSaved callback dispatched on the callback dispatcher thread after the write
     */
    public void startTimer(String slotId, Runnable onSaved) {
        workerExecutor.execute(() -> {
            taskDao.startTimer(slotId, LocalTime.now());
            callbackDispatcher.execute(onSaved);
        });
    }

    /**
     * Stops the manual timer for a slot by recording the current time as its end.
     *
     * <p>Counterpart to {@link #startTimer}. See that method for the distinction between
     * manual timers and the two-phase check-off completion flow.
     *
     * @param slotId  the slot UUID to stop timing
     * @param onSaved callback dispatched on the callback dispatcher thread after the write
     */
    public void stopTimer(String slotId, Runnable onSaved) {
        workerExecutor.execute(() -> {
            taskDao.stopTimer(slotId, LocalTime.now());
            callbackDispatcher.execute(onSaved);
        });
    }

    public void deleteTask(String taskId, Runnable onDeleted) {
        workerExecutor.execute(() -> {
            taskDao.deleteTaskGraph(taskId);
            callbackDispatcher.execute(onDeleted);
        });
    }
}
