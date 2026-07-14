package com.autosecretary.features.task.application;

import androidx.room.RoomDatabase;

import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskCategoryWindowDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCategoryWindow;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Reverses the most recent assistant apply by popping and restoring the top
 * {@link TaskChangeUndoHolder.Snapshot}. Repeatable — each call steps one apply further back until
 * the stack is empty; an empty stack is a no-op ({@code onDone} receives {@code false}).
 */
public class UndoTaskChangesUseCase {

    private final RoomDatabase database;
    private final TaskDao taskDao;
    private final TaskCategoryDao categoryDao;
    private final TaskCategoryWindowDao windowDao;
    private final TaskChangeUndoHolder undoHolder;
    private final ExecutorService dbExecutor;
    private final Executor callbackDispatcher;

    public UndoTaskChangesUseCase(RoomDatabase database,
                                  TaskDao taskDao,
                                  TaskCategoryDao categoryDao,
                                  TaskCategoryWindowDao windowDao,
                                  TaskChangeUndoHolder undoHolder,
                                  ExecutorService dbExecutor,
                                  Executor callbackDispatcher) {
        this.database = database;
        this.taskDao = taskDao;
        this.categoryDao = categoryDao;
        this.windowDao = windowDao;
        this.undoHolder = undoHolder;
        this.dbExecutor = dbExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    public boolean hasUndo() {
        return !undoHolder.isEmpty();
    }

    /** Undoes the last apply; {@code onDone} receives {@code true} if something was restored. */
    public void undoLast(Consumer<Boolean> onDone) {
        dbExecutor.execute(() -> {
            TaskChangeUndoHolder.Snapshot snapshot = undoHolder.pop();
            if (snapshot == null) {
                callbackDispatcher.execute(() -> onDone.accept(false));
                return;
            }
            database.runInTransaction(() -> restore(snapshot));
            callbackDispatcher.execute(() -> onDone.accept(true));
        });
    }

    private void restore(TaskChangeUndoHolder.Snapshot snapshot) {
        // Remove everything the apply created.
        for (String taskId : snapshot.createdTaskIds()) {
            taskDao.deleteTaskGraph(taskId);
        }
        for (String categoryId : snapshot.createdCategoryIds()) {
            categoryDao.clearCategoryFromTasks(categoryId);
            windowDao.deleteByCategory(categoryId);
            categoryDao.delete(categoryId);
        }
        // Restore prior categories/windows first, then tasks (recreates deleted rows, reverts updates,
        // and restores categoryIds that were cascade-cleared when a category was deleted).
        for (TaskCategory category : snapshot.priorCategories()) {
            categoryDao.write(category);
        }
        for (TaskCategoryWindow window : snapshot.priorWindows()) {
            windowDao.write(window);
        }
        for (Task task : snapshot.priorTasks()) {
            taskDao.write(task);
        }
    }
}
